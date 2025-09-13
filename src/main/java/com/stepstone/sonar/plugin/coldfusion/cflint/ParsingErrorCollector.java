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

import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects and categorizes parsing errors for comprehensive error reporting.
 * Implements RIR-002: Enhanced Error Handling Strategy from the CFLint Robustness Improvements Specification.
 * 
 * This class provides:
 * - Error categorization (HTML structure, CFML syntax, parser limitations)
 * - Error aggregation and reporting
 * - Pattern recognition for common issues
 * - Recovery strategy recommendations
 */
public class ParsingErrorCollector {
    
    private final Logger logger = Loggers.get(ParsingErrorCollector.class);
    
    // Thread-safe collections for concurrent error collection
    private final Map<String, ParseError> errorsByFile = new ConcurrentHashMap<>();
    private final Map<ErrorCategory, Integer> errorCategoryCounts = new ConcurrentHashMap<>();
    private final List<String> errorPatterns = new ArrayList<>();
    
    /**
     * Enumeration of error categories for better error classification
     */
    public enum ErrorCategory {
        HTML_STRUCTURE_MISSING("Missing HTML Document Structure"),
        HTML_MALFORMED("Malformed HTML/CFML Tags"),
        PARSER_NULL_POINTER("Parser Null Safety Issues"),
        JERICHO_PARSER_FAILURE("Jericho HTML Parser Failures"),
        CFML_SYNTAX_ERROR("CFML Syntax Errors"),
        FILE_ACCESS_ERROR("File Access/IO Errors"),
        UNKNOWN_ERROR("Unknown/Uncategorized Errors");
        
        private final String description;
        
        ErrorCategory(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Represents a parsing error with context information
     */
    public static class ParseError {
        private final String filePath;
        private final String errorMessage;
        private final ErrorCategory category;
        private final Exception originalException;
        private final long timestamp;
        
        public ParseError(String filePath, String errorMessage, ErrorCategory category, Exception originalException) {
            this.filePath = filePath;
            this.errorMessage = errorMessage;
            this.category = category;
            this.originalException = originalException;
            this.timestamp = System.currentTimeMillis();
        }
        
        // Getters
        public String getFilePath() { return filePath; }
        public String getErrorMessage() { return errorMessage; }
        public ErrorCategory getCategory() { return category; }
        public Exception getOriginalException() { return originalException; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Adds a parsing error with automatic categorization
     * 
     * @param filePath Path to the file that failed to parse
     * @param exception The exception that occurred during parsing
     */
    public void addError(String filePath, Exception exception) {
        String errorMessage = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        ErrorCategory category = categorizeError(errorMessage, exception);
        
        ParseError parseError = new ParseError(filePath, errorMessage, category, exception);
        errorsByFile.put(filePath, parseError);
        
        // Update category counts
        errorCategoryCounts.merge(category, 1, Integer::sum);
        
        logger.debug("Categorized parsing error for file {}: {} - {}", filePath, category, errorMessage);
    }
    
    /**
     * Categorizes errors based on error message patterns and exception types.
     * Implements pattern recognition for common CFLint parsing issues.
     * 
     * @param errorMessage The error message from the exception
     * @param exception The original exception
     * @return Categorized error type
     */
    private ErrorCategory categorizeError(String errorMessage, Exception exception) {
        if (errorMessage == null) {
            errorMessage = "";
        }
        
        String lowerMessage = errorMessage.toLowerCase();
        
        // Pattern matching for known error types
        if (lowerMessage.contains("nullpointerexception") || 
            exception instanceof NullPointerException ||
            lowerMessage.contains("parsertag") ||
            lowerMessage.contains("tag.getelement")) {
            return ErrorCategory.PARSER_NULL_POINTER;
        }
        
        if (lowerMessage.contains("jericho") ||
            lowerMessage.contains("html parser") ||
            lowerMessage.contains("malformed html")) {
            return ErrorCategory.JERICHO_PARSER_FAILURE;
        }
        
        if (lowerMessage.contains("missing") && 
            (lowerMessage.contains("doctype") || 
             lowerMessage.contains("<html>") || 
             lowerMessage.contains("<head>") || 
             lowerMessage.contains("<body>"))) {
            return ErrorCategory.HTML_STRUCTURE_MISSING;
        }
        
        if (lowerMessage.contains("tag") && 
            (lowerMessage.contains("malformed") || 
             lowerMessage.contains("unclosed") || 
             lowerMessage.contains("invalid"))) {
            return ErrorCategory.HTML_MALFORMED;
        }
        
        if (lowerMessage.contains("cfml") ||
            lowerMessage.contains("coldfusion") ||
            lowerMessage.contains("cflint")) {
            return ErrorCategory.CFML_SYNTAX_ERROR;
        }
        
        if (exception instanceof java.io.IOException ||
            lowerMessage.contains("file") ||
            lowerMessage.contains("access") ||
            lowerMessage.contains("permission")) {
            return ErrorCategory.FILE_ACCESS_ERROR;
        }
        
        return ErrorCategory.UNKNOWN_ERROR;
    }
    
    /**
     * Generates a comprehensive error report with statistics and recommendations
     * 
     * @return Formatted error report
     */
    public String generateErrorReport() {
        if (errorsByFile.isEmpty()) {
            return "No parsing errors detected.";
        }
        
        StringBuilder report = new StringBuilder();
        report.append("\n=== CFLint Parsing Error Report ===\n");
        report.append(String.format("Total files with errors: %d\n\n", errorsByFile.size()));
        
        // Category breakdown
        report.append("Error Categories:\n");
        for (Map.Entry<ErrorCategory, Integer> entry : errorCategoryCounts.entrySet()) {
            double percentage = (double) entry.getValue() / errorsByFile.size() * 100;
            report.append(String.format("  %s: %d (%.1f%%)\n", 
                         entry.getKey().getDescription(), entry.getValue(), percentage));
        }
        
        report.append("\n");
        
        // Recommendations based on error patterns
        addRecommendations(report);
        
        // Detailed file listing (limited to first 10 for brevity)
        report.append("Failed Files (showing first 10):\n");
        int count = 0;
        for (ParseError error : errorsByFile.values()) {
            if (count >= 10) {
                report.append(String.format("  ... and %d more files\n", errorsByFile.size() - 10));
                break;
            }
            report.append(String.format("  %s: [%s] %s\n", 
                         error.getFilePath(), error.getCategory(), error.getErrorMessage()));
            count++;
        }
        
        return report.toString();
    }
    
    /**
     * Adds targeted recommendations based on the types of errors encountered
     * 
     * @param report StringBuilder to append recommendations to
     */
    private void addRecommendations(StringBuilder report) {
        report.append("Recommendations:\n");
        
        if (errorCategoryCounts.containsKey(ErrorCategory.HTML_STRUCTURE_MISSING)) {
            report.append("  • Add proper HTML document structure (DOCTYPE, <html>, <head>, <body>) to template fragments\n");
        }
        
        if (errorCategoryCounts.containsKey(ErrorCategory.HTML_MALFORMED)) {
            report.append("  • Review HTML/CFML tag structure - ensure proper opening/closing tags\n");
            report.append("  • Move <script> and <style> elements inside <head> tags\n");
        }
        
        if (errorCategoryCounts.containsKey(ErrorCategory.PARSER_NULL_POINTER) ||
            errorCategoryCounts.containsKey(ErrorCategory.JERICHO_PARSER_FAILURE)) {
            report.append("  • Consider using lenient parsing mode for legacy templates\n");
            report.append("  • These files may be template fragments - consider configuration options\n");
        }
        
        if (errorCategoryCounts.containsKey(ErrorCategory.CFML_SYNTAX_ERROR)) {
            report.append("  • Review CFML syntax - check for invalid tag usage or malformed expressions\n");
        }
        
        report.append("  • Enable debug logging for detailed error information\n");
        report.append("  • Consider implementing Phase 2 of CFLint Robustness Improvements for enhanced error recovery\n\n");
    }
    
    /**
     * Returns the total number of files with parsing errors
     * 
     * @return Error count
     */
    public int getErrorCount() {
        return errorsByFile.size();
    }
    
    /**
     * Returns the count for a specific error category
     * 
     * @param category Error category to check
     * @return Count of errors in this category
     */
    public int getErrorCountByCategory(ErrorCategory category) {
        return errorCategoryCounts.getOrDefault(category, 0);
    }
    
    /**
     * Clears all collected errors. Useful for testing or analysis reset.
     */
    public void clearErrors() {
        errorsByFile.clear();
        errorCategoryCounts.clear();
        errorPatterns.clear();
    }
    
    /**
     * Returns success rate based on total files vs. error count
     * 
     * @param totalFiles Total number of files attempted
     * @return Success rate as percentage (0-100)
     */
    public double getSuccessRate(int totalFiles) {
        if (totalFiles == 0) return 100.0;
        return ((double) (totalFiles - errorsByFile.size()) / totalFiles) * 100.0;
    }
}
