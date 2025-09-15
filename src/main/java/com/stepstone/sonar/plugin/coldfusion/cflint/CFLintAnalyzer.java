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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamException;

import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import com.cflint.api.CFLintAPI;
import com.cflint.api.CFLintResult;
import com.cflint.config.CFLintPluginInfo;
import com.cflint.config.ConfigBuilder;
import com.cflint.config.ConfigUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.stepstone.sonar.plugin.coldfusion.ColdFusionPlugin;

/**
 * Enhanced CFLint analyzer with comprehensive error handling and robustness improvements.
 * 
 * Implements Phase 1 of the CFLint Robustness Improvements Specification:
 * - RIR-001: Null Safety Implementation (at plugin level)
 * - RIR-002: Enhanced Error Handling Strategy
 * 
 * This analyzer provides graceful degradation when CFLint encounters parsing errors,
 * ensuring that malformed HTML/CFML files don't prevent analysis of other files.
 */

public class CFLintAnalyzer {

    private final Configuration settings;
    private final FileSystem fs;
    private final Logger logger = Loggers.get(CFLintAnalyzer.class);
    private final ParsingErrorCollector errorCollector = new ParsingErrorCollector();
    private final HTMLPreprocessor htmlPreprocessor;
    private final FallbackAnalyzer fallbackAnalyzer;
    
    // Configuration-driven parsing behavior (RIR-005)
    private final ParsingMode parsingMode;
    private final boolean skipMalformedFiles;
    private final String errorReportingLevel;
    private final int errorThreshold;
    private final boolean legacySupport;
    
    // Error tracking for robustness reporting
    private int totalFiles = 0;
    private int successfullyParsedFiles = 0;
    private int failedFiles = 0;

    public CFLintAnalyzer(SensorContext sensorContext) {
        Preconditions.checkNotNull(sensorContext);

        this.settings = sensorContext.config();
        this.fs = sensorContext.fileSystem();
        
        // Initialize configuration-driven behavior (RIR-005)
        this.parsingMode = ParsingMode.fromString(
            settings.get(ColdFusionPlugin.PARSING_MODE).orElse("LENIENT")
        );
        this.skipMalformedFiles = settings.getBoolean(ColdFusionPlugin.SKIP_MALFORMED_FILES).orElse(true);
        this.errorReportingLevel = settings.get(ColdFusionPlugin.ERROR_REPORTING_LEVEL).orElse("SUMMARY");
        this.errorThreshold = settings.getInt(ColdFusionPlugin.ERROR_THRESHOLD).orElse(50);
        this.legacySupport = settings.getBoolean(ColdFusionPlugin.LEGACY_SUPPORT).orElse(true);
        
        // Initialize HTML preprocessor with configuration
        boolean preprocessingEnabled = settings.getBoolean(ColdFusionPlugin.HTML_PREPROCESSING).orElse(true);
        this.htmlPreprocessor = new HTMLPreprocessor(
            preprocessingEnabled,
            true, // addHtmlWrapper
            true, // fixUnclosedTags  
            true  // fixMalformedAttributes
        );
        
        // Initialize fallback analyzer
        boolean fallbackEnabled = settings.getBoolean(ColdFusionPlugin.FALLBACK_ANALYSIS).orElse(true);
        int maxFallbackIssues = settings.getInt(ColdFusionPlugin.FALLBACK_MAX_ISSUES).orElse(50);
        this.fallbackAnalyzer = new FallbackAnalyzer(fallbackEnabled, maxFallbackIssues);
        
        logger.info("CFLint Analyzer initialized with: mode={}, skipMalformed={}, legacySupport={}, errorThreshold={}%, preprocessing={}, fallback={}", 
                   parsingMode, skipMalformedFiles, legacySupport, errorThreshold, preprocessingEnabled, fallbackEnabled);
    }

    public void analyze(File configFile) throws IOException, XMLStreamException {
        List<String> filesToScan = new ArrayList<>();

        for (InputFile file : fs.inputFiles(fs.predicates().hasLanguage(ColdFusionPlugin.LANGUAGE_KEY))) {
            filesToScan.add(file.absolutePath());
        }
        
        totalFiles = filesToScan.size();
        logger.info("Starting CFLint analysis of {} ColdFusion files with {} mode", totalFiles, parsingMode);
        
        // Implementation based on RIR-001: Enhanced Error Handling Strategy
        // Use parsing mode to determine analysis strategy
        boolean batchSuccess = false;
        
        if (parsingMode.shouldAttemptBatchAnalysis()) {
            batchSuccess = attemptBatchAnalysis(configFile, filesToScan);
        }
        
        if (!batchSuccess && parsingMode.shouldContinueOnError()) {
            logger.warn("Batch CFLint analysis failed or skipped, attempting individual file analysis for better error recovery");
            attemptIndividualFileAnalysis(configFile, filesToScan);
        } else if (!batchSuccess && !parsingMode.shouldContinueOnError()) {
            // In STRICT mode, fail completely if batch analysis fails
            throw new IOException("CFLint analysis failed in STRICT mode - no error recovery attempted");
        }
        
        logAnalysisResults();
    }
    
    /**
     * Attempts to analyze all files in a single batch operation.
     * This is the preferred approach for performance but may fail completely on parsing errors.
     * 
     * @param configFile CFLint configuration file
     * @param filesToScan List of files to analyze
     * @return true if batch analysis succeeded, false otherwise
     */
    private boolean attemptBatchAnalysis(File configFile, List<String> filesToScan) {
        Writer xmlwriter = null;
        try {
            xmlwriter = createXMLWriter(fs.workDir() + File.separator + "cflint-result.xml", StandardCharsets.UTF_8);

            CFLintPluginInfo cflintPluginInfo;
            ConfigBuilder cflintConfigBuilder;
            CFLintAPI linter;
            
            try {
                cflintPluginInfo = ConfigUtils.loadDefaultPluginInfo();
                cflintConfigBuilder = new ConfigBuilder(cflintPluginInfo);
                cflintConfigBuilder.addCustomConfig(configFile.getPath());
                linter = new CFLintAPI(cflintConfigBuilder.build());
                linter.setVerbose(false); // Reduce verbosity to minimize log noise
            } catch (Exception configException) {
                logger.error("Failed to initialize CFLint configuration for batch analysis: {}", configException.getMessage());
                throw new Exception("CFLint configuration failed", configException);
            }
            
            logger.info("Attempting batch analysis of {} files", filesToScan.size());

            CFLintResult lintResult = linter.scan(filesToScan);

            try {
                lintResult.writeXml(xmlwriter);
                successfullyParsedFiles = totalFiles; // All files processed successfully
                logger.info("Batch CFLint analysis completed successfully for all {} files", totalFiles);
                return true;
            } catch(Exception ce) {
                logger.error("Failed to write CFLint results XML after successful parsing: {}", ce.getMessage());
                throw new Exception(ce);
            }
        } catch(Exception ce) {
            logger.warn("Batch CFLint analysis failed with error: {} - Will attempt individual file analysis", ce.getMessage());
            logger.debug("Full batch analysis error details:", ce);
            
            // Record the batch failure for error reporting - check for specific error types
            if (isJerichoParsingError(ce)) {
                errorCollector.addError("BATCH_ANALYSIS_JERICHO", ce);
            } else {
                errorCollector.addError("BATCH_ANALYSIS", ce);
            }
            
            return false;
        } finally {
            if (xmlwriter != null) {
                try {
                    xmlwriter.close();
                } catch (IOException e) {
                    logger.debug("Failed to close XML writer in batch analysis: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * Determines if an exception is related to Jericho HTML parser failures.
     * This helps categorize parsing errors for better reporting.
     * 
     * @param exception Exception to analyze
     * @return true if this appears to be a Jericho parsing error
     */
    private boolean isJerichoParsingError(Exception exception) {
        if (exception == null) return false;
        
        String message = exception.getMessage();
        if (message == null) message = "";
        
        // Check for NullPointerException patterns common in Jericho parser
        if (exception instanceof NullPointerException) {
            return true;
        }
        
        // Check for Jericho-specific error patterns
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("jericho") ||
               lowerMessage.contains("htmlparser") ||
               lowerMessage.contains("tag.getelement") ||
               lowerMessage.contains("parsertag") ||
               lowerMessage.contains("net.htmlparser");
    }
    
    /**
     * Analyzes files individually to provide maximum robustness.
     * This approach sacrifices performance for error recovery, ensuring that parsing failures
     * in individual files don't prevent analysis of other files.
     * 
     * @param configFile CFLint configuration file
     * @param filesToScan List of files to analyze
     * @throws IOException if XML output cannot be written
     */
    private void attemptIndividualFileAnalysis(File configFile, List<String> filesToScan) throws IOException {
        Writer xmlwriter = null;
        
        try {
            xmlwriter = createXMLWriter(fs.workDir() + File.separator + "cflint-result.xml", StandardCharsets.UTF_8);
            
            // Write XML header for combined results
            xmlwriter.write("<issues version=\"1.0\">\n");
            
            CFLintPluginInfo cflintPluginInfo;
            ConfigBuilder cflintConfigBuilder;
            CFLintAPI linter;
            
            try {
                cflintPluginInfo = ConfigUtils.loadDefaultPluginInfo();
                cflintConfigBuilder = new ConfigBuilder(cflintPluginInfo);
                cflintConfigBuilder.addCustomConfig(configFile.getPath());
                linter = new CFLintAPI(cflintConfigBuilder.build());
                linter.setVerbose(true);
            } catch (Exception e) {
                logger.error("Failed to initialize CFLint API for individual file analysis: {}", e.getMessage());
                throw new IOException("CFLint initialization failed", e);
            }
            
            for (String filePath : filesToScan) {
                analyzeIndividualFile(linter, filePath, xmlwriter);
            }
            
            // Write XML footer
            xmlwriter.write("</issues>\n");
            
        } finally {
            if (xmlwriter != null) {
                try {
                    xmlwriter.close();
                } catch (IOException e) {
                    logger.error("Failed to close XML writer: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * Analyzes a single file with comprehensive error handling.
     * Implements RIR-001: Null Safety Implementation at the plugin level.
     * Now includes HTML preprocessing to fix common parsing issues.
     * 
     * @param linter CFLint API instance
     * @param filePath Path to the file to analyze
     * @param xmlwriter XML writer for results
     */
    private void analyzeIndividualFile(CFLintAPI linter, String filePath, Writer xmlwriter) {
        String actualFilePath = filePath;
        Path temporaryFile = null;
        
        try {
            logger.debug("Analyzing file: {}", filePath);
            
            // Apply HTML preprocessing if enabled
            if (htmlPreprocessor.isEnabled()) {
                try {
                    String preprocessedContent = htmlPreprocessor.preprocessFile(filePath);
                    
                    // Only create temporary file if content was actually changed
                    String originalContent = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
                    if (!preprocessedContent.equals(originalContent)) {
                        temporaryFile = htmlPreprocessor.createTemporaryPreprocessedFile(filePath, preprocessedContent);
                        actualFilePath = temporaryFile.toString();
                        logger.debug("Using preprocessed file for analysis: {} -> {}", filePath, actualFilePath);
                    }
                } catch (Exception preprocessException) {
                    logger.warn("Preprocessing failed for file {}: {} - Using original file", 
                               filePath, preprocessException.getMessage());
                    // Continue with original file if preprocessing fails
                }
            }
            
            List<String> singleFile = new ArrayList<>();
            singleFile.add(actualFilePath);
            
            CFLintResult result = linter.scan(singleFile);
            
            // Extract and write individual file results (simplified XML extraction)
            // This is a basic implementation - in production, we'd need proper XML parsing
            String resultXml = getResultXmlContent(result);
            if (resultXml != null && !resultXml.trim().isEmpty()) {
                // If we used a temporary file, we need to replace its path with the original in results
                if (temporaryFile != null) {
                    resultXml = resultXml.replace(temporaryFile.toString(), filePath);
                }
                xmlwriter.write(resultXml);
                xmlwriter.write("\n");
            }
            
            successfullyParsedFiles++;
            logger.debug("Successfully analyzed file: {}", filePath);
            
        } catch (Exception e) {
            failedFiles++;
            
            // Enhanced error categorization and recording
            categorizeAndRecordError(filePath, e);
            
            logger.warn("Failed to analyze file: {} - Error: {}", filePath, e.getMessage());
            logger.debug("Detailed error for file {}: ", filePath, e);
            
            // Attempt fallback analysis if enabled
            String fallbackResults = attemptFallbackAnalysis(filePath);
            if (fallbackResults != null && !fallbackResults.trim().isEmpty()) {
                try {
                    xmlwriter.write(fallbackResults);
                    xmlwriter.write("\n");
                    logger.debug("Fallback analysis provided results for file: {}", filePath);
                } catch (IOException fallbackWriteException) {
                    logger.warn("Failed to write fallback analysis results for file {}: {}", 
                               filePath, fallbackWriteException.getMessage());
                }
            }
            
            // Write error information as comment in XML for debugging
            try {
                xmlwriter.write(String.format("<!-- PARSING_ERROR: File=%s, Error=%s, Type=%s -->\n", 
                                            filePath, 
                                            e.getMessage().replaceAll("--", "- -"),
                                            categorizeErrorType(e)));
            } catch (IOException ioException) {
                logger.error("Failed to write error comment for file {}: {}", filePath, ioException.getMessage());
            }
        } finally {
            // Clean up temporary file if created
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                    logger.debug("Cleaned up temporary file: {}", temporaryFile);
                } catch (Exception cleanupException) {
                    logger.debug("Failed to clean up temporary file {}: {}", 
                                temporaryFile, cleanupException.getMessage());
                    // Not critical - file will be cleaned on JVM exit
                }
            }
        }
    }
    
    /**
     * Categorizes parsing errors and records them appropriately.
     * 
     * @param filePath File that failed to parse
     * @param exception Exception that occurred
     */
    private void categorizeAndRecordError(String filePath, Exception exception) {
        if (isJerichoParsingError(exception)) {
            errorCollector.addError(filePath, exception);
        } else if (isCFMLSyntaxError(exception)) {
            errorCollector.addError(filePath, exception);
        } else if (isHTMLStructureError(exception)) {
            errorCollector.addError(filePath, exception);
        } else {
            errorCollector.addError(filePath, exception);
        }
    }
    
    /**
     * Returns a simple string categorization of error type for logging.
     * 
     * @param exception Exception to categorize
     * @return Error type description
     */
    private String categorizeErrorType(Exception exception) {
        if (isJerichoParsingError(exception)) {
            return "JERICHO_PARSER";
        } else if (isCFMLSyntaxError(exception)) {
            return "CFML_SYNTAX";
        } else if (isHTMLStructureError(exception)) {
            return "HTML_STRUCTURE";
        } else {
            return "GENERAL_PARSING";
        }
    }
    
    /**
     * Checks if an exception indicates a CFML syntax error.
     * 
     * @param exception Exception to check
     * @return true if this appears to be a CFML syntax error
     */
    private boolean isCFMLSyntaxError(Exception exception) {
        if (exception == null) return false;
        
        String message = exception.getMessage();
        if (message == null) return false;
        
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("cfml") ||
               lowerMessage.contains("coldfusion") ||
               lowerMessage.contains("syntax error") ||
               lowerMessage.contains("parse error");
    }
    
    /**
     * Checks if an exception indicates HTML structure problems.
     * 
     * @param exception Exception to check
     * @return true if this appears to be an HTML structure error
     */
    private boolean isHTMLStructureError(Exception exception) {
        if (exception == null) return false;
        
        String message = exception.getMessage();
        if (message == null) return false;
        
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("html") ||
               lowerMessage.contains("malformed") ||
               lowerMessage.contains("tag") ||
               lowerMessage.contains("element");
    }
    
    /**
     * Attempts fallback analysis when primary CFLint parsing fails.
     * 
     * @param filePath Path to file that failed primary analysis
     * @return XML results from fallback analysis, or null if not available
     */
    private String attemptFallbackAnalysis(String filePath) {
        if (!fallbackAnalyzer.isEnabled()) {
            return null;
        }
        
        try {
            logger.debug("Attempting fallback analysis for file: {}", filePath);
            
            List<FallbackAnalyzer.FallbackIssue> issues = fallbackAnalyzer.analyzeFile(filePath);
            if (issues.isEmpty()) {
                logger.debug("Fallback analysis found no issues in file: {}", filePath);
                return null;
            }
            
            String xmlOutput = fallbackAnalyzer.generateXmlOutput(issues);
            logger.info("Fallback analysis found {} issues in file: {}", issues.size(), filePath);
            
            return xmlOutput;
            
        } catch (Exception fallbackException) {
            logger.warn("Fallback analysis failed for file {}: {}", filePath, fallbackException.getMessage());
            logger.debug("Fallback analysis error details for {}: ", filePath, fallbackException);
            return null;
        }
    }
    
    /**
     * Extracts XML content from CFLintResult.
     * This is a simplified implementation for the individual file analysis approach.
     * 
     * @param result CFLint analysis result
     * @return XML content as string, or null if extraction fails
     */
    private String getResultXmlContent(CFLintResult result) {
        try {
            // Use StringWriter to capture XML output
            java.io.StringWriter stringWriter = new java.io.StringWriter();
            result.writeXml(stringWriter);
            String fullXml = stringWriter.toString();
            
            // Extract just the issue elements (skip XML declaration and root element)
            // This is a basic implementation - production code would use proper XML parsing
            int issuesStart = fullXml.indexOf("<issue");
            int issuesEnd = fullXml.lastIndexOf("</issue>") + 8;
            
            if (issuesStart >= 0 && issuesEnd > issuesStart) {
                return fullXml.substring(issuesStart, issuesEnd);
            }
            
            return "";
            
        } catch (Exception e) {
            logger.debug("Failed to extract XML content from CFLintResult: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Logs comprehensive analysis results including error recovery statistics.
     * Provides visibility into parsing robustness as specified in RIR-002.
     */
    private void logAnalysisResults() {
        double successRate = errorCollector.getSuccessRate(totalFiles);
        
        if (failedFiles == 0) {
            logger.info("CFLint analysis completed successfully: {}/{} files analyzed (100%)", 
                       successfullyParsedFiles, totalFiles);
        } else {
            logger.warn("CFLint analysis completed with partial success: {}/{} files analyzed ({:.1f}%), {} files failed", 
                       successfullyParsedFiles, totalFiles, successRate, failedFiles);
            
            if (successRate < 85) {
                logger.error("CFLint analysis success rate ({:.1f}%) is below recommended threshold (85%). " +
                           "Consider reviewing failed files for common HTML/CFML structure issues.", successRate);
            }
        }
        
        // Log detailed error report if there are failures and reporting is enabled
        if (failedFiles > 0 && shouldLogDetailedErrors()) {
            String errorReport = errorCollector.generateErrorReport();
            logger.info("Detailed parsing error analysis:\n{}", errorReport);
        }
        
        // Evaluate success against threshold
        evaluateAnalysisSuccess(successRate);
        
        // Log summary recommendations
        if (failedFiles > 0) {
            logger.info("Common fixes for parsing errors: " +
                       "1) Add proper HTML document structure (DOCTYPE, html, head, body tags), " +
                       "2) Move script/style elements inside head tags, " +
                       "3) Ensure proper tag closure. " +
                       "See debug logs for specific file errors.");
        }
        
        // Log success metrics for monitoring
        logSuccessMetrics(successRate);
    }
    
    /**
     * Determines if detailed error logging should be performed based on configuration
     * 
     * @return true if detailed errors should be logged
     */
    private boolean shouldLogDetailedErrors() {
        return "DETAILED".equalsIgnoreCase(errorReportingLevel) || 
               ("SUMMARY".equalsIgnoreCase(errorReportingLevel) && failedFiles > 0);
    }
    
    /**
     * Evaluates analysis success against configured thresholds and takes appropriate action
     * 
     * @param successRate Analysis success rate percentage
     */
    private void evaluateAnalysisSuccess(double successRate) {
        double failureRate = 100.0 - successRate;
        
        if (failureRate > errorThreshold) {
            String message = String.format(
                "CFLint analysis failure rate ({:.1f}%) exceeds configured threshold (%d%%). " +
                "Consider reviewing parsing configuration or addressing HTML/CFML structure issues.",
                failureRate, errorThreshold);
                
            if (parsingMode == ParsingMode.STRICT) {
                logger.error(message);
                // In strict mode, this would be an error condition
            } else {
                logger.warn(message);
            }
        } else if (successRate < parsingMode.getRecommendedErrorThreshold()) {
            logger.warn("CFLint analysis success rate ({:.1f}%) is below recommended threshold for {} mode ({}%). " +
                       "Consider reviewing failed files for common HTML/CFML structure issues.", 
                       successRate, parsingMode, parsingMode.getRecommendedErrorThreshold());
        }
    }
    
    /**
     * Logs success metrics in a format suitable for monitoring and alerting
     * 
     * @param successRate Analysis success rate percentage
     */
    private void logSuccessMetrics(double successRate) {
        // Structured logging for monitoring systems
        logger.info("CFLINT_ANALYSIS_METRICS: totalFiles={}, successfulFiles={}, failedFiles={}, successRate={:.1f}%, " +
                   "parsingMode={}, legacySupport={}, errorThreshold={}%, " +
                   "parserNullPointerErrors={}, htmlStructureErrors={}, jerichoParserErrors={}",
                   totalFiles, 
                   successfullyParsedFiles, 
                   failedFiles, 
                   successRate,
                   parsingMode,
                   legacySupport,
                   errorThreshold,
                   errorCollector.getErrorCountByCategory(ParsingErrorCollector.ErrorCategory.PARSER_NULL_POINTER),
                   errorCollector.getErrorCountByCategory(ParsingErrorCollector.ErrorCategory.HTML_STRUCTURE_MISSING),
                   errorCollector.getErrorCountByCategory(ParsingErrorCollector.ErrorCategory.JERICHO_PARSER_FAILURE));
    }

    // protected File extractCflintJar() throws IOException {
    //     return new CFLintExtractor(fs.workDir()).extract();
    // }

    protected void addCflintJavaOpts(Command command) {
        final String cflintJavaOpts = settings.get(ColdFusionPlugin.CFLINT_JAVA_OPTS).orElse("");
        if (!Strings.isNullOrEmpty(cflintJavaOpts)) {
            final String[] arguments = cflintJavaOpts.split(" ");
            for (String argument : arguments) {
                command.addArgument(argument);
            }
        }
    }

    private Writer createXMLWriter(final String xmlOutFile, final Charset encoding) throws IOException {
        final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(xmlOutFile), encoding);
        try {
            out.append(String.format("<?xml version=\"1.0\" encoding=\"%s\" ?>%n", encoding));
        } catch (final IOException e) {
            throw new IOException(e);
        }
        return out;
    }

}
