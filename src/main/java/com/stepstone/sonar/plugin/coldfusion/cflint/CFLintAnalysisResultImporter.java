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

    public CFLintAnalysisResultImporter(FileSystem fs, SensorContext sensorContext) {
        this.fs = fs;
        this.sensorContext = sensorContext;
        this.includeResolver = new IncludeResolver(fs);
    }

    public void parse(File file) throws IOException, XMLStreamException {

        try (FileReader reader = new FileReader(file)) {
            parse(reader);
        } catch (XMLStreamException | IOException e) {
            logger.error("", e);
            throw e;
        } finally {
            closeXmlStream();
        }
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
        while (stream.hasNext()) {
            int next = stream.next();

            if (next == XMLStreamConstants.END_ELEMENT && "issue".equals(stream.getLocalName())) {
                break;
            }
            else if (next == XMLStreamConstants.START_ELEMENT) {

                String tagName = stream.getLocalName();

                if ("location".equals(tagName)) {
                    LocationAttributes locationAttributes = new LocationAttributes(stream);

                    InputFile inputFile = fs.inputFile(fs.predicates().hasAbsolutePath(locationAttributes.getFile()));
                    createNewIssue(issueAttributes, locationAttributes, inputFile);
                }
            }
        }
    }

    private void createNewIssue(IssueAttributes issueAttributes, LocationAttributes locationAttributes, InputFile inputFile) {
        if(issueAttributes == null){
            logger.debug("Problem creating issue for file {} issueAttributes is null", inputFile);
        }
        if(locationAttributes == null){
            logger.debug("Problem creating issue for file {} locationAttributes is null", inputFile);
        }
        if(inputFile==null){
            logger.debug("Problem creating issue for file inputFile is null");
        }
        if(issueAttributes == null || locationAttributes == null || inputFile == null){
            return;
        }

        // Enhanced virtual line number detection and resolution
        if(locationAttributes.getLine().isPresent() && locationAttributes.getLine().get() > inputFile.lines()){
            logger.debug("Virtual line number detected - issue line {} > file lines {}", 
                       locationAttributes.getLine().get(), inputFile.lines());
            
            // Try to resolve virtual line number using include processing
            boolean resolved = handleIncludeProcessingIssue(issueAttributes, locationAttributes, inputFile);
            if (resolved) {
                logger.debug("Successfully resolved virtual line number using include processing");
                return;
            }
            
            // If resolution failed, log the error and return
            logger.error("Problem creating issue for file {}, issue is line {} but file has {} lines", 
                        inputFile, locationAttributes.getLine().get(), inputFile.lines());
            return;
        }

        logger.debug("create New Issue {} for file {}", issueAttributes, inputFile.filename());
        final NewIssue issue = sensorContext.newIssue();

        final NewIssueLocation issueLocation = issue.newLocation();
        issueLocation.on(inputFile);
        issueLocation.at(inputFile.selectLine(locationAttributes.getLine().get()));
        issueLocation.message(locationAttributes.getMessage().get());

        issue.forRule(RuleKey.of(ColdFusionPlugin.REPOSITORY_KEY, issueAttributes.getId().get()));
        issue.at(issueLocation);
        issue.save();
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
                logger.warn("Could not resolve virtual line number {} in file {}", 
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
