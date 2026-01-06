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

package com.stepstone.sonar.plugin.coldfusion.cflint.include;

import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ColdFusion files to extract cfinclude directives and build 
 * a mapping of virtual line numbers to actual file locations.
 */
public class IncludeMapper {
    
    private static final Logger logger = Loggers.get(IncludeMapper.class);
    
    // Regex patterns for cfinclude directive parsing
    private static final Pattern CFINCLUDE_PATTERN = Pattern.compile(
        "<cfinclude\\s+template\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>", 
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    private final FileSystem fileSystem;
    
    public IncludeMapper(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }
    
    /**
     * Builds a complete include mapping for a given file, including nested includes.
     * 
     * @param mainFile The main file to analyze
     * @return List of include mappings representing the virtual file structure
     */
    public List<IncludeMapping> buildIncludeMap(InputFile mainFile) {
        List<IncludeMapping> mappings = new ArrayList<>();
        
        try {
            buildIncludeMapRecursive(mainFile, mappings, 1, new ArrayList<>());
        } catch (IOException e) {
            logger.error("Failed to build include map for file {}: {}", mainFile.filename(), e.getMessage());
        }
        
        return mappings;
    }
    
    /**
     * Recursively builds include mappings, handling nested includes.
     */
    private int buildIncludeMapRecursive(InputFile file, List<IncludeMapping> mappings, 
                                       int currentVirtualLine, List<String> includeStack) throws IOException {
        
        // Prevent infinite recursion
        String filePath = file.absolutePath();
        if (includeStack.contains(filePath)) {
            logger.debug("Circular include detected: {} -> {}", String.join(" -> ", includeStack), filePath);
            return currentVirtualLine;
        }
        
        includeStack.add(filePath);
        
        try {
            String content = file.contents();
            String[] lines = content.split("\\r?\\n");
            
            int virtualLineCounter = currentVirtualLine;
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                Matcher matcher = CFINCLUDE_PATTERN.matcher(line);
                
                if (matcher.find()) {
                    String templatePath = matcher.group(1);
                    InputFile includedFile = resolveIncludePath(file, templatePath);
                    
                    if (includedFile != null) {
                        logger.debug("Found include at line {} in {}: template={}", 
                                   i + 1, file.filename(), templatePath);
                        
                        // Calculate virtual line range for the included file
                        int includeStartLine = virtualLineCounter + 1;
                        int includeEndLine = includeStartLine + includedFile.lines() - 1;
                        
                        // Create mapping for the included file
                        IncludeMapping mapping = new IncludeMapping(
                            includeStartLine, includeEndLine, includedFile, 1, templatePath
                        );
                        mappings.add(mapping);
                        
                        // Process nested includes recursively
                        virtualLineCounter = buildIncludeMapRecursive(
                            includedFile, mappings, includeStartLine, new ArrayList<>(includeStack)
                        );
                        
                        // Update counter to end of included content
                        virtualLineCounter = includeEndLine;
                        
                    } else {
                        logger.debug("Could not resolve include template '{}' in file {}", 
                                  templatePath, file.filename());
                    }
                } else {
                    // Regular line, just increment counter
                    virtualLineCounter++;
                }
            }
            
            return virtualLineCounter;
            
        } finally {
            includeStack.remove(filePath);
        }
    }
    
    /**
     * Resolves an include template path to an actual InputFile.
     */
    private InputFile resolveIncludePath(InputFile sourceFile, String templatePath) {
        try {
            // Handle absolute paths (starting with /)
            if (templatePath.startsWith("/")) {
                // Look for file relative to project root
                String projectRoot = fileSystem.baseDir().getAbsolutePath();
                Path absolutePath = Paths.get(projectRoot, templatePath.substring(1));
                
                InputFile resolved = fileSystem.inputFile(
                    fileSystem.predicates().hasAbsolutePath(absolutePath.toString())
                );
                
                if (resolved != null) {
                    return resolved;
                }
                
                // Try with common ColdFusion file extensions
                for (String ext : new String[]{".cfm", ".cfc", ".cfml"}) {
                    if (!templatePath.endsWith(ext)) {
                        Path pathWithExt = Paths.get(absolutePath.toString() + ext);
                        resolved = fileSystem.inputFile(
                            fileSystem.predicates().hasAbsolutePath(pathWithExt.toString())
                        );
                        if (resolved != null) {
                            return resolved;
                        }
                    }
                }
            } else {
                // Handle relative paths
                Path sourceDir = Paths.get(sourceFile.absolutePath()).getParent();
                Path relativePath = sourceDir.resolve(templatePath);
                
                InputFile resolved = fileSystem.inputFile(
                    fileSystem.predicates().hasAbsolutePath(relativePath.toString())
                );
                
                if (resolved != null) {
                    return resolved;
                }
                
                // Try with common ColdFusion file extensions
                for (String ext : new String[]{".cfm", ".cfc", ".cfml"}) {
                    if (!templatePath.endsWith(ext)) {
                        Path pathWithExt = Paths.get(relativePath.toString() + ext);
                        resolved = fileSystem.inputFile(
                            fileSystem.predicates().hasAbsolutePath(pathWithExt.toString())
                        );
                        if (resolved != null) {
                            return resolved;
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.debug("Error resolving include path '{}' from {}: {}", 
                        templatePath, sourceFile.filename(), e.getMessage());
        }
        
        return null;
    }
}
