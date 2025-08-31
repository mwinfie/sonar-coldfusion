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

import org.sonar.api.batch.fs.InputFile;

/**
 * Represents a mapping between virtual line numbers (as reported by CFLint 1.5.9) 
 * and actual file locations for ColdFusion include processing.
 */
public class IncludeMapping {
    
    private final int virtualStartLine;
    private final int virtualEndLine;
    private final InputFile actualFile;
    private final int actualStartLine;
    private final String includeTemplate;
    
    /**
     * Creates a new include mapping.
     * 
     * @param virtualStartLine The starting line in the virtual concatenated file
     * @param virtualEndLine The ending line in the virtual concatenated file  
     * @param actualFile The actual file that contains the code
     * @param actualStartLine The starting line in the actual file (usually 1)
     * @param includeTemplate The template path from the cfinclude directive
     */
    public IncludeMapping(int virtualStartLine, int virtualEndLine, InputFile actualFile, 
                         int actualStartLine, String includeTemplate) {
        this.virtualStartLine = virtualStartLine;
        this.virtualEndLine = virtualEndLine;
        this.actualFile = actualFile;
        this.actualStartLine = actualStartLine;
        this.includeTemplate = includeTemplate;
    }
    
    /**
     * Checks if a virtual line number falls within this mapping's range.
     */
    public boolean containsVirtualLine(int virtualLine) {
        return virtualLine >= virtualStartLine && virtualLine <= virtualEndLine;
    }
    
    /**
     * Converts a virtual line number to the corresponding actual line number.
     */
    public int getActualLineNumber(int virtualLine) {
        if (!containsVirtualLine(virtualLine)) {
            throw new IllegalArgumentException(
                String.format("Virtual line %d is not within range [%d, %d]", 
                             virtualLine, virtualStartLine, virtualEndLine));
        }
        return actualStartLine + (virtualLine - virtualStartLine);
    }
    
    // Getters
    public int getVirtualStartLine() { return virtualStartLine; }
    public int getVirtualEndLine() { return virtualEndLine; }
    public InputFile getActualFile() { return actualFile; }
    public int getActualStartLine() { return actualStartLine; }
    public String getIncludeTemplate() { return includeTemplate; }
    
    @Override
    public String toString() {
        return String.format("IncludeMapping{virtual=[%d-%d], file=%s, actual=[%d+], template='%s'}", 
                           virtualStartLine, virtualEndLine, 
                           actualFile != null ? actualFile.filename() : "null",
                           actualStartLine, includeTemplate);
    }
}
