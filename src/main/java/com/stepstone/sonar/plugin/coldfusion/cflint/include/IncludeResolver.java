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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves virtual line numbers (as reported by CFLint 1.5.9 with inline include processing)
 * to actual file locations and line numbers.
 */
public class IncludeResolver {
    
    private static final Logger logger = Loggers.get(IncludeResolver.class);
    
    private final IncludeMapper includeMapper;
    private final ConcurrentHashMap<String, List<IncludeMapping>> includeCache = new ConcurrentHashMap<>();
    
    public IncludeResolver(FileSystem fileSystem) {
        this.includeMapper = new IncludeMapper(fileSystem);
    }
    
    /**
     * Represents the result of resolving a virtual line number.
     */
    public static class ResolvedLocation {
        private final InputFile file;
        private final int lineNumber;
        private final boolean isIncluded;
        private final String includeTemplate;
        
        public ResolvedLocation(InputFile file, int lineNumber, boolean isIncluded, String includeTemplate) {
            this.file = file;
            this.lineNumber = lineNumber;
            this.isIncluded = isIncluded;
            this.includeTemplate = includeTemplate;
        }
        
        public InputFile getFile() { return file; }
        public int getLineNumber() { return lineNumber; }
        public boolean isIncluded() { return isIncluded; }
        public String getIncludeTemplate() { return includeTemplate; }
        
        @Override
        public String toString() {
            return String.format("ResolvedLocation{file=%s, line=%d, included=%s, template='%s'}", 
                               file.filename(), lineNumber, isIncluded, includeTemplate);
        }
    }
    
    /**
     * Resolves a virtual line number to an actual file and line number.
     * 
     * @param mainFile The main file being analyzed
     * @param virtualLineNumber The line number as reported by CFLint
     * @return ResolvedLocation containing the actual file and line, or null if unresolvable
     */
    public ResolvedLocation resolveVirtualLine(InputFile mainFile, int virtualLineNumber) {
        
        // If line number is within the main file bounds, no resolution needed
        if (virtualLineNumber <= mainFile.lines()) {
            return new ResolvedLocation(mainFile, virtualLineNumber, false, null);
        }
        
        // Get or build include mappings for this file
        List<IncludeMapping> mappings = getIncludeMappings(mainFile);
        
        // Find the mapping that contains this virtual line
        for (IncludeMapping mapping : mappings) {
            if (mapping.containsVirtualLine(virtualLineNumber)) {
                int actualLine = mapping.getActualLineNumber(virtualLineNumber);
                
                logger.debug("Resolved virtual line {} in {} to line {} in {}", 
                           virtualLineNumber, mainFile.filename(), 
                           actualLine, mapping.getActualFile().filename());
                
                return new ResolvedLocation(
                    mapping.getActualFile(), 
                    actualLine, 
                    true, 
                    mapping.getIncludeTemplate()
                );
            }
        }
        
        // Could not resolve - might be beyond all includes
        // This is expected when CFLint inlines includes - log at DEBUG to reduce noise
        logger.debug("Could not resolve virtual line {} in file {} (file has {} lines, {} include mappings)", 
                   virtualLineNumber, mainFile.filename(), mainFile.lines(), mappings.size());
        
        return null;
    }
    
    /**
     * Gets or builds include mappings for a file, using cache for performance.
     */
    private List<IncludeMapping> getIncludeMappings(InputFile file) {
        String cacheKey = file.absolutePath();
        
        return includeCache.computeIfAbsent(cacheKey, key -> {
            logger.debug("Building include mappings for file: {}", file.filename());
            List<IncludeMapping> mappings = includeMapper.buildIncludeMap(file);
            
            if (!mappings.isEmpty()) {
                logger.debug("Built {} include mappings for {}: {}", 
                           mappings.size(), file.filename(), mappings);
            }
            
            return mappings;
        });
    }
    
    /**
     * Clears the include cache (useful for testing or when files change).
     */
    public void clearCache() {
        includeCache.clear();
        logger.debug("Include cache cleared");
    }
    
    /**
     * Gets cache statistics for monitoring.
     */
    public int getCacheSize() {
        return includeCache.size();
    }
}
