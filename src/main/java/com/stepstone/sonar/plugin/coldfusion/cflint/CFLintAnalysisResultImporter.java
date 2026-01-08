/*
Copyright 2016 StepStone GmbH

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.stepstone.sonar.plugin.coldfusion.cflint;

import com.stepstone.sonar.plugin.coldfusion.ColdFusionPlugin;
import com.stepstone.sonar.plugin.coldfusion.cflint.xml.IssueAttributes;
import com.stepstone.sonar.plugin.coldfusion.cflint.xml.LocationAttributes;
import com.stepstone.sonar.plugin.coldfusion.cflint.include.IncludeResolver;

import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class CFLintAnalysisResultImporter {

    private final FileSystem fs;
    private final SensorContext sensorContext;
    private final IncludeResolver includeResolver;
    private XMLStreamReader stream;
    private final Logger logger = Loggers.get(CFLintAnalysisResultImporter.class);
    
    // Performance tracking
    private int totalIssuesProcessed = 0;
    private int issuesCreated = 0;
    private int issuesSkippedVirtualLines = 0;
    private int lastReportedProgress = 0;

    public CFLintAnalysisResultImporter(FileSystem fs, SensorContext sensorContext) {
        this.fs = fs;
        this.sensorContext = sensorContext;
        this.includeResolver = new IncludeResolver(fs);
    }

    public void parse(File file) throws IOException, XMLStreamException {

        logger.info("Starting to import CFLint analysis results from {}", file.getName());
        
        // Check file size first - only count issues for reasonable files
        long fileSizeMB = file.length() / (1024 * 1024);
        logger.info("CFLint XML file size: {}MB", fileSizeMB);
        
        // EMERGENCY: Abort if file is massive (indicates CFLint inline include explosion)
        if (fileSizeMB > 1000) {
            String errorMsg = String.format(
                "CRITICAL: CFLint XML file is %dMB! This indicates CFLint ran with inline include processing, " +
                "generating millions of issues. ABORTING import to prevent hours-long processing. " +
                "Solution: Check CFLint configuration to disable inline includes, or delete .scannerwork and rerun.", 
                fileSizeMB
            );
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        
        // Only count issues for reasonably sized files (< 100MB)
        int totalIssues = 0;
        if (fileSizeMB < 100) {
            totalIssues = countTotalIssues(file);
            if (totalIssues > 0) {
                logger.info("Total issues found in CFLint XML: {} (file size: {}MB)", totalIssues, fileSizeMB);
                
                // EMERGENCY: Abort if issue count is absurdly high
                if (totalIssues > 1000000) {
                    String errorMsg = String.format(
                        "CRITICAL: CFLint generated %d issues! This is abnormal (expected ~170K for 2944 files). " +
                        "This indicates inline include processing created an issue explosion. ABORTING import. " +
                        "Solution: Fix CFLint configuration to disable inline includes.", 
                        totalIssues
                    );
                    logger.error(errorMsg);
                    throw new IllegalStateException(errorMsg);
                }
            }
        } else {
            logger.warn("Skipping issue count for large file ({}MB) - would delay import", fileSizeMB);
        }
        
        long startTime = System.currentTimeMillis();
        
        try (FileReader reader = new FileReader(file)) {
            parse(reader);
        } catch (XMLStreamException | IOException e) {
            logger.error("", e);
            throw e;
        } finally {
            closeXmlStream();
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("CFLint result import completed in {}ms: {} issues processed, {} created, {} skipped (virtual lines)", 
                       duration, totalIssuesProcessed, issuesCreated, issuesSkippedVirtualLines);
        }
    }
    
    private int countTotalIssues(File file) {
        int count = 0;
        try (FileReader reader = new FileReader(file)) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            XMLStreamReader countStream = factory.createXMLStreamReader(reader);
            
            while (countStream.hasNext()) {
                if (countStream.next() == XMLStreamConstants.START_ELEMENT) {
                    if ("issue".equals(countStream.getLocalName())) {
                        count++;
                    }
                }
            }
            countStream.close();
        } catch (Exception e) {
            logger.warn("Could not count total issues, will proceed without total: {}", e.getMessage());
        }
        return count;
    }

    private void parse(FileReader reader) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        stream = factory.createXMLStreamReader(reader);

        parse();
    }

    private void parse() throws XMLStreamException {
        while (stream.hasNext()) {
            if (stream.next() == XMLStreamConstants.START_ELEMENT) {
                String tagName = stream.getLocalName();

                if ("issue".equals(tagName)) {
                    handleIssueTag(new IssueAttributes(stream));
                }
            }
        }
    }

    private void handleIssueTag(IssueAttributes issueAttributes) throws XMLStreamException {
        // Only process the FIRST location for each issue to avoid creating thousands
        // of duplicate issues for the same rule violation
        boolean firstLocationProcessed = false;
        
        while (stream.hasNext()) {
            int next = stream.next();

            if (next == XMLStreamConstants.END_ELEMENT && "issue".equals(stream.getLocalName())) {
                break;
            }
            else if (next == XMLStreamConstants.START_ELEMENT) {

                String tagName = stream.getLocalName();

                if ("location".equals(tagName)) {
                    LocationAttributes locationAttributes = new LocationAttributes(stream);

                    // Only create issue for the first location
                    if (!firstLocationProcessed) {
                        InputFile inputFile = fs.inputFile(fs.predicates().hasAbsolutePath(locationAttributes.getFile()));
                        createNewIssue(issueAttributes, locationAttributes, inputFile);
                        firstLocationProcessed = true;
                    }
                }
            }
        }
    }

    private void createNewIssue(IssueAttributes issueAttributes, LocationAttributes locationAttributes, InputFile inputFile) {
        // Check for missing attributes first, before counting
        if(issueAttributes == null || locationAttributes == null || inputFile == null){
            return;
        }
        
        totalIssuesProcessed++;
        
        // Report progress every 10,000 issues to show activity
        if (totalIssuesProcessed - lastReportedProgress >= 10000) {
            logger.info("Import progress: {} issues processed, {} created, {} skipped", 
                       totalIssuesProcessed, issuesCreated, issuesSkippedVirtualLines);
            lastReportedProgress = totalIssuesProcessed;
        }

        // Quick check: Enhanced virtual line number detection and resolution
        if(locationAttributes.getLine().isPresent() && locationAttributes.getLine().get() > inputFile.lines()){
            // Virtual line from CFLint include inlining - skip without detailed logging
            issuesSkippedVirtualLines++;
            
            // Only try to resolve if we haven't hit a threshold of skipped issues
            // This avoids expensive resolution attempts for files with thousands of virtual lines
            if (issuesSkippedVirtualLines < 1000 || issuesSkippedVirtualLines % 1000 == 0) {
                boolean resolved = handleIncludeProcessingIssue(issueAttributes, locationAttributes, inputFile);
                if (resolved) {
                    issuesCreated++;
                    return;
                }
            }
            return;
        }

        // Create normal issue (not virtual)
        final NewIssue issue = sensorContext.newIssue();
        final NewIssueLocation issueLocation = issue.newLocation();
        issueLocation.on(inputFile);
        issueLocation.at(inputFile.selectLine(locationAttributes.getLine().get()));
        issueLocation.message(locationAttributes.getMessage().get());

        issue.forRule(RuleKey.of(ColdFusionPlugin.REPOSITORY_KEY, issueAttributes.getId().get()));
        issue.at(issueLocation);
        issue.save();
        issuesCreated++;
    }

    /**
     * Handles issues with virtual line numbers from CFLint 1.5.9's inline include processing.
     * Attempts to resolve the virtual line number to the actual included file and line.
     * 
     * @param issueAttributes The issue attributes from CFLint
     * @param locationAttributes The location attributes with virtual line number
     * @param inputFile The main file being analyzed
     * @return true if the issue was successfully resolved and created, false otherwise
     */
    private boolean handleIncludeProcessingIssue(IssueAttributes issueAttributes, 
                                                LocationAttributes locationAttributes, 
                                                InputFile inputFile) {
        logger.debug("Attempting to resolve virtual line number {} in file {}", 
                   locationAttributes.getLine().get(), inputFile.filename());
        
        try {
            IncludeResolver.ResolvedLocation resolved = includeResolver.resolveVirtualLine(
                inputFile, locationAttributes.getLine().get()
            );
            
            if (resolved != null) {
                logger.debug("Resolved to file {} line {}", 
                           resolved.getFile().filename(), resolved.getLineNumber());
                
                // Create issue at the resolved location
                final NewIssue issue = sensorContext.newIssue();
                final NewIssueLocation issueLocation = issue.newLocation();
                
                issueLocation.on(resolved.getFile());
                issueLocation.at(resolved.getFile().selectLine(resolved.getLineNumber()));
                
                // Enhanced message for included files
                String originalMessage = locationAttributes.getMessage().orElse("CFLint Issue");
                String enhancedMessage = resolved.isIncluded() 
                    ? String.format("%s (from included file: %s)", originalMessage, resolved.getIncludeTemplate())
                    : originalMessage;
                    
                issueLocation.message(enhancedMessage);
                
                issue.forRule(RuleKey.of(ColdFusionPlugin.REPOSITORY_KEY, issueAttributes.getId().get()));
                issue.at(issueLocation);
                issue.save();
                
                logger.debug("Created resolved issue for {} at line {} in {}", 
                           issueAttributes.getId().orElse("unknown"), 
                           resolved.getLineNumber(), 
                           resolved.getFile().filename());
                
                return true;
            } else {
                // Expected for CFLint inlined includes - log at DEBUG to reduce noise
                logger.debug("Could not resolve virtual line number {} in file {}", 
                           locationAttributes.getLine().get(), inputFile.filename());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Failed to create issue for file {} at line {}: {}", 
                        inputFile.filename(), locationAttributes.getLine().get(), e.getMessage());
            return false;
        }
    }

    private void closeXmlStream() throws XMLStreamException {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception e) {
                throw e;
            }
        }
    }
}
