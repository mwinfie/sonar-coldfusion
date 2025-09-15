package com.stepstone.sonar.plugin.coldfusion.cflint;

/**
 * Enumeration of different types of parsing errors that can occur during CFLint analysis.
 * This helps categorize errors for better reporting and debugging.
 */
public enum ParsingErrorType {
    
    /**
     * Errors caused by Jericho HTML parser failures, typically due to malformed HTML structure
     */
    JERICHO_PARSER_FAILURE("Jericho Parser Failure"),
    
    /**
     * Errors in CFML syntax that prevent proper parsing
     */
    CFML_SYNTAX_ERROR("CFML Syntax Error"),
    
    /**
     * Errors due to missing or malformed HTML structure (DOCTYPE, html, head, body tags)
     */
    HTML_STRUCTURE_MISSING("HTML Structure Missing"),
    
    /**
     * General parsing errors that don't fit into other categories
     */
    GENERAL_PARSING_ERROR("General Parsing Error"),
    
    /**
     * Configuration-related errors during CFLint setup
     */
    CONFIGURATION_ERROR("Configuration Error"),
    
    /**
     * File I/O errors during analysis
     */
    FILE_IO_ERROR("File I/O Error"),
    
    /**
     * Memory or resource-related errors
     */
    RESOURCE_ERROR("Resource Error");
    
    private final String displayName;
    
    ParsingErrorType(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * Gets the human-readable display name for this error type.
     * 
     * @return Display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
