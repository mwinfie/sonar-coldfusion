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

package com.stepstone.sonar.plugin.coldfusion;

import com.stepstone.sonar.plugin.coldfusion.profile.ColdFusionSonarWayProfile;
import com.stepstone.sonar.plugin.coldfusion.rules.ColdFusionSonarRulesDefinition;

import org.sonar.api.Plugin;
import org.sonar.api.config.PropertyDefinition;

public class ColdFusionPlugin implements Plugin {

    public static final String LANGUAGE_KEY = "cf";
    public static final String LANGUAGE_NAME = "ColdFusion";

    public static final String FILE_SUFFIXES_KEY = "sonar.cf.file.suffixes";
    public static final String FILE_SUFFIXES_DEFVALUE = ".cfc,.cfm";

    public static final String REPOSITORY_KEY = "coldfusionsquid";
    public static final String REPOSITORY_NAME = "SonarQube";

    public static final String CFLINT_JAVA = "sonar.cf.cflint.java";
    public static final String CFLINT_JAVA_OPTS = "sonar.cf.cflint.java.opts";
    
    // Configuration keys for robustness improvements (RIR-005)
    public static final String PARSING_MODE = "sonar.cf.parsing.mode";
    public static final String SKIP_MALFORMED_FILES = "sonar.cf.parsing.skipMalformed";
    public static final String ERROR_REPORTING_LEVEL = "sonar.cf.parsing.errorReporting";
    public static final String ERROR_THRESHOLD = "sonar.cf.parsing.errorThreshold";
    public static final String LEGACY_SUPPORT = "sonar.cf.parsing.legacySupport";

    @Override
    public void define(Context context) {
        context.addExtensions(
                ColdFusion.class,
                ColdFusionSensor.class,
                ColdFusionSonarRulesDefinition.class,
                ColdFusionSonarWayProfile.class
        );

        // Add property definitions programmatically
        context.addExtensions(
                PropertyDefinition.builder(FILE_SUFFIXES_KEY)
                        .defaultValue(FILE_SUFFIXES_DEFVALUE)
                        .name("File suffixes")
                        .description("Comma-separated list of suffixes of files to analyze.")
                        .multiValues(true)
                        .build(),

                PropertyDefinition.builder(CFLINT_JAVA)
                        .defaultValue("java")
                        .name("Java executable")
                        .description("Path to the Java executable for running CFLint")
                        .build(),

                PropertyDefinition.builder(CFLINT_JAVA_OPTS)
                        .defaultValue("")
                        .name("Java executable options")
                        .description("Additional parameters passed to java process. E.g. -Xmx1g")
                        .build(),
                
                // CFLint Robustness Improvements Configuration (RIR-005)
                PropertyDefinition.builder(PARSING_MODE)
                        .defaultValue("LENIENT")
                        .name("CFLint Parsing Mode")
                        .description("Controls CFLint parsing strictness: STRICT (fail on errors), LENIENT (continue with warnings), FRAGMENT (minimal validation for template fragments)")
                        .build(),
                
                PropertyDefinition.builder(SKIP_MALFORMED_FILES)
                        .defaultValue("true")
                        .name("Skip Malformed Files")
                        .description("When enabled, files with parsing errors are skipped rather than failing the entire analysis")
                        .build(),
                
                PropertyDefinition.builder(ERROR_REPORTING_LEVEL)
                        .defaultValue("SUMMARY")
                        .name("Error Reporting Level")
                        .description("Controls verbosity of parsing error reports: NONE (no error details), SUMMARY (error statistics), DETAILED (full error information)")
                        .build(),
                
                PropertyDefinition.builder(ERROR_THRESHOLD)
                        .defaultValue("50")
                        .name("Error Threshold Percentage")
                        .description("Maximum percentage of files that can fail parsing before analysis is considered problematic (0-100)")
                        .build(),
                
                PropertyDefinition.builder(LEGACY_SUPPORT)
                        .defaultValue("true")
                        .name("Legacy ColdFusion Support")
                        .description("Enables enhanced support for legacy ColdFusion templates with non-standard HTML structure")
                        .build()
        );
    }
}
