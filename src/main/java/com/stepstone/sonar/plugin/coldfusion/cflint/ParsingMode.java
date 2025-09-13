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

/**
 * Parsing modes for CFLint analysis with different levels of error tolerance.
 * Implements RIR-005: Configuration and Flexibility from the CFLint Robustness Improvements Specification.
 */
public enum ParsingMode {
    
    /**
     * Strict mode: Fail analysis on any parsing error (original CFLint behavior)
     * Recommended for: New projects with clean HTML/CFML structure
     */
    STRICT("Strict - Fail on any parsing error"),
    
    /**
     * Lenient mode: Continue analysis with warnings for recoverable errors
     * Recommended for: Most enterprise environments with mixed code quality
     */
    LENIENT("Lenient - Continue with warnings on recoverable errors"),
    
    /**
     * Fragment mode: Minimal HTML validation, assume template fragments
     * Recommended for: Legacy systems with extensive use of template fragments
     */
    FRAGMENT("Fragment - Minimal validation for template fragments");
    
    private final String description;
    
    ParsingMode(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Parses a string value to ParsingMode, with fallback to LENIENT
     * 
     * @param value String value to parse
     * @return Corresponding ParsingMode, or LENIENT if value is invalid
     */
    public static ParsingMode fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LENIENT;
        }
        
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Default to LENIENT for invalid values
            return LENIENT;
        }
    }
    
    /**
     * Determines if batch analysis should be attempted in this mode
     * 
     * @return true if batch analysis should be tried first
     */
    public boolean shouldAttemptBatchAnalysis() {
        // In STRICT mode, we want to fail fast, so batch analysis makes sense
        // In LENIENT and FRAGMENT modes, we expect some failures, so individual analysis is more appropriate
        return this == STRICT;
    }
    
    /**
     * Determines if analysis should continue after encountering parsing errors
     * 
     * @return true if analysis should continue despite errors
     */
    public boolean shouldContinueOnError() {
        return this != STRICT;
    }
    
    /**
     * Gets the recommended error threshold percentage for this mode
     * 
     * @return Maximum acceptable error percentage (0-100)
     */
    public int getRecommendedErrorThreshold() {
        switch (this) {
            case STRICT:
                return 0;  // No errors acceptable
            case LENIENT:
                return 15; // Up to 15% errors acceptable
            case FRAGMENT:
                return 30; // Up to 30% errors acceptable for legacy fragments
            default:
                return 15;
        }
    }
}
