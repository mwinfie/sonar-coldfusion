package com.stepstone.sonar.plugin.coldfusion.cflint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * HTMLPreprocessor handles common HTML and CFML structural issues that cause 
 * Jericho HTML parser failures in CFLint. This class implements Phase 2 of 
 * the plugin mitigation strategy by cleaning problematic content before analysis.
 * 
 * Common issues addressed:
 * - Missing HTML structure (html, head, body tags)
 * - Unclosed CFML tags that confuse HTML parsers
 * - Malformed HTML attributes in CFML context
 * - Script tag issues with CFML content
 * - DOCTYPE and encoding problems
 * 
 * @author SonarQube ColdFusion Plugin Team
 * @version 3.0.0
 */
public class HTMLPreprocessor {
    
    private static final Logger logger = LoggerFactory.getLogger(HTMLPreprocessor.class);
    
    // Pattern for detecting files that need HTML structure wrapping
    private static final Pattern NEEDS_HTML_WRAPPER = Pattern.compile(
        "(?i)^(?!.*<html[^>]*>).*(?:<cf|<%)|(?:<script|<style|<link|<meta)",
        Pattern.DOTALL
    );
    
    // Pattern for unclosed CFML tags that need special handling
    private static final Pattern UNCLOSED_CF_TAGS = Pattern.compile(
        "(?i)<(cf(?:set|param|include|location|header|cookie|log|dump|abort|break|continue|exit)\\b[^>]*)(?<!/)>",
        Pattern.MULTILINE
    );
    
    // Pattern for problematic script tags with CFML
    private static final Pattern SCRIPT_WITH_CFML = Pattern.compile(
        "(?i)<script[^>]*>([^<]*(?:<cf[^>]*>[^<]*)*)</script>",
        Pattern.DOTALL
    );
    
    // Pattern for malformed attributes in HTML tags
    private static final Pattern MALFORMED_ATTRIBUTES = Pattern.compile(
        "(?i)(<[a-z]+[^>]*?)\\s+([a-z-]+)\\s*=\\s*([^\"'\\s>]+)(?=\\s|>)",
        Pattern.MULTILINE
    );
    
    private final boolean enabled;
    private final boolean addHtmlWrapper;
    private final boolean fixUnclosedTags;
    private final boolean fixMalformedAttributes;
    
    /**
     * Creates HTMLPreprocessor with default settings.
     */
    public HTMLPreprocessor() {
        this(true, true, true, true);
    }
    
    /**
     * Creates HTMLPreprocessor with custom configuration.
     * 
     * @param enabled Whether preprocessing is enabled
     * @param addHtmlWrapper Whether to add HTML wrapper to fragment files
     * @param fixUnclosedTags Whether to fix unclosed CFML tags
     * @param fixMalformedAttributes Whether to fix malformed HTML attributes
     */
    public HTMLPreprocessor(boolean enabled, boolean addHtmlWrapper, 
                           boolean fixUnclosedTags, boolean fixMalformedAttributes) {
        this.enabled = enabled;
        this.addHtmlWrapper = addHtmlWrapper;
        this.fixUnclosedTags = fixUnclosedTags;
        this.fixMalformedAttributes = fixMalformedAttributes;
        
        logger.debug("HTMLPreprocessor initialized - enabled: {}, htmlWrapper: {}, unclosedTags: {}, attributes: {}", 
                    new Object[]{enabled, addHtmlWrapper, fixUnclosedTags, fixMalformedAttributes});
    }
    
    /**
     * Preprocesses a CFML file to fix common issues that cause Jericho parser failures.
     * 
     * @param filePath Path to the CFML file to preprocess
     * @return Preprocessed content as string, or original content if preprocessing disabled
     * @throws IOException if file cannot be read
     */
    public String preprocessFile(String filePath) throws IOException {
        if (!enabled) {
            return Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
        }
        
        logger.debug("Preprocessing file: {}", filePath);
        
        String originalContent = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
        String processedContent = originalContent;
        
        try {
            // Step 1: Fix malformed HTML attributes first
            if (fixMalformedAttributes) {
                processedContent = fixMalformedHtmlAttributes(processedContent);
            }
            
            // Step 2: Fix unclosed CFML tags
            if (fixUnclosedTags) {
                processedContent = fixUnclosedCfmlTags(processedContent);
            }
            
            // Step 3: Handle script tags with CFML content
            processedContent = fixScriptTagsWithCfml(processedContent);
            
            // Step 4: Add HTML wrapper if needed (do this last)
            if (addHtmlWrapper && needsHtmlWrapper(processedContent)) {
                processedContent = wrapWithHtmlStructure(processedContent, filePath);
            }
            
            if (!processedContent.equals(originalContent)) {
                logger.debug("File {} was preprocessed - {} chars -> {} chars", 
                           filePath, originalContent.length(), processedContent.length());
            }
            
            return processedContent;
            
        } catch (Exception e) {
            logger.warn("Failed to preprocess file {}: {} - Using original content", 
                       filePath, e.getMessage());
            logger.debug("Preprocessing error details for {}: ", filePath, e);
            return originalContent; // Fallback to original on any error
        }
    }
    
    /**
     * Determines if content needs HTML wrapper structure.
     * 
     * @param content File content to check
     * @return true if content appears to need HTML structure
     */
    private boolean needsHtmlWrapper(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // Check if already has HTML structure
        if (content.toLowerCase().contains("<html")) {
            return false;
        }
        
        // Check if it looks like a CFML fragment or has HTML-like content
        return NEEDS_HTML_WRAPPER.matcher(content).find();
    }
    
    /**
     * Wraps content with minimal HTML structure for Jericho parser compatibility.
     * 
     * @param content Original file content
     * @param filePath File path for logging
     * @return Content wrapped with HTML structure
     */
    private String wrapWithHtmlStructure(String content, String filePath) {
        logger.debug("Adding HTML wrapper to file: {}", filePath);
        
        StringBuilder wrapped = new StringBuilder();
        wrapped.append("<!DOCTYPE html>\n");
        wrapped.append("<html>\n");
        wrapped.append("<head>\n");
        wrapped.append("    <title>ColdFusion File</title>\n");
        wrapped.append("    <meta charset=\"UTF-8\">\n");
        wrapped.append("</head>\n");
        wrapped.append("<body>\n");
        wrapped.append("<!-- SONAR_PREPROCESSOR: HTML wrapper added for parsing -->\n");
        wrapped.append(content);
        wrapped.append("\n</body>\n");
        wrapped.append("</html>\n");
        
        return wrapped.toString();
    }
    
    /**
     * Fixes unclosed CFML tags that are self-closing by nature.
     * 
     * @param content File content to process
     * @return Content with unclosed CFML tags properly closed
     */
    private String fixUnclosedCfmlTags(String content) {
        Matcher matcher = UNCLOSED_CF_TAGS.matcher(content);
        StringBuffer result = new StringBuffer();
        
        int fixCount = 0;
        while (matcher.find()) {
            String tagContent = matcher.group(1);
            String replacement = "<" + tagContent + " />";
            matcher.appendReplacement(result, replacement);
            fixCount++;
        }
        matcher.appendTail(result);
        
        if (fixCount > 0) {
            logger.debug("Fixed {} unclosed CFML tags", fixCount);
        }
        
        return result.toString();
    }
    
    /**
     * Fixes malformed HTML attributes by adding proper quotes.
     * 
     * @param content File content to process
     * @return Content with properly quoted HTML attributes
     */
    private String fixMalformedHtmlAttributes(String content) {
        Matcher matcher = MALFORMED_ATTRIBUTES.matcher(content);
        StringBuffer result = new StringBuffer();
        
        int fixCount = 0;
        while (matcher.find()) {
            String tagStart = matcher.group(1);
            String attrName = matcher.group(2);
            String attrValue = matcher.group(3);
            
            // Add quotes around unquoted attribute values
            String replacement = tagStart + " " + attrName + "=\"" + attrValue + "\"";
            matcher.appendReplacement(result, replacement);
            fixCount++;
        }
        matcher.appendTail(result);
        
        if (fixCount > 0) {
            logger.debug("Fixed {} malformed HTML attributes", fixCount);
        }
        
        return result.toString();
    }
    
    /**
     * Handles script tags that contain CFML code which can confuse HTML parsers.
     * 
     * @param content File content to process
     * @return Content with script/CFML conflicts resolved
     */
    private String fixScriptTagsWithCfml(String content) {
        Matcher matcher = SCRIPT_WITH_CFML.matcher(content);
        StringBuffer result = new StringBuffer();
        
        int fixCount = 0;
        while (matcher.find()) {
            String scriptContent = matcher.group(1);
            
            // If script contains CFML, wrap the CFML in CDATA to protect it
            if (scriptContent.toLowerCase().contains("<cf")) {
                String replacement = "<script type=\"text/javascript\">\n//<![CDATA[\n" + 
                                   scriptContent + "\n//]]>\n</script>";
                matcher.appendReplacement(result, replacement);
                fixCount++;
            }
        }
        matcher.appendTail(result);
        
        if (fixCount > 0) {
            logger.debug("Fixed {} script tags with CFML content", fixCount);
        }
        
        return result.toString();
    }
    
    /**
     * Creates a temporary preprocessed file for CFLint analysis.
     * This allows the original file to remain unchanged while providing
     * cleaned content for parsing.
     * 
     * @param originalPath Path to original file
     * @param preprocessedContent Cleaned content
     * @return Path to temporary preprocessed file
     * @throws IOException if temporary file cannot be created
     */
    public Path createTemporaryPreprocessedFile(String originalPath, String preprocessedContent) throws IOException {
        Path originalFilePath = Paths.get(originalPath);
        String fileName = originalFilePath.getFileName().toString();
        String baseName = fileName.contains(".") ? 
                         fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String extension = fileName.contains(".") ? 
                          fileName.substring(fileName.lastIndexOf('.')) : "";
        
        Path tempFile = Files.createTempFile("sonar_preprocessed_" + baseName, extension);
        Files.writeString(tempFile, preprocessedContent, StandardCharsets.UTF_8);
        
        // Mark for deletion on JVM exit
        tempFile.toFile().deleteOnExit();
        
        logger.debug("Created temporary preprocessed file: {} -> {}", originalPath, tempFile);
        return tempFile;
    }
    
    /**
     * Checks if preprocessing is enabled.
     * 
     * @return true if preprocessing is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}
