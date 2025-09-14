package com.stepstone.sonar.plugin.coldfusion.cflint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FallbackAnalyzer provides basic CFML code analysis using regex patterns
 * when the primary CFLint/Jericho parser fails. This implements Phase 3 of 
 * the plugin mitigation strategy by detecting common CFML issues through
 * pattern matching rather than full AST parsing.
 * 
 * This analyzer focuses on high-confidence, low-false-positive rules that
 * can be detected reliably through text analysis:
 * - SQL injection vulnerabilities
 * - Cross-site scripting (XSS) risks  
 * - Performance issues (queries in loops)
 * - Security misconfigurations
 * - Code style violations
 * 
 * @author SonarQube ColdFusion Plugin Team
 * @version 3.0.0
 */
public class FallbackAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(FallbackAnalyzer.class);
    
    /**
     * Represents a simple rule violation found by regex analysis
     */
    public static class FallbackIssue {
        private final String ruleId;
        private final String ruleName;
        private final String severity;
        private final String message;
        private final String filePath;
        private final int lineNumber;
        private final int columnNumber;
        private final String evidence;
        
        public FallbackIssue(String ruleId, String ruleName, String severity, 
                           String message, String filePath, int lineNumber, 
                           int columnNumber, String evidence) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.severity = severity;
            this.message = message;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
            this.evidence = evidence;
        }
        
        // Getters
        public String getRuleId() { return ruleId; }
        public String getRuleName() { return ruleName; }
        public String getSeverity() { return severity; }
        public String getMessage() { return message; }
        public String getFilePath() { return filePath; }
        public int getLineNumber() { return lineNumber; }
        public int getColumnNumber() { return columnNumber; }
        public String getEvidence() { return evidence; }
    }
    
    /**
     * Internal rule definition for regex-based analysis
     */
    private static class FallbackRule {
        final String id;
        final String name;
        final String severity;
        final Pattern pattern;
        final String messageTemplate;
        final boolean multiline;
        
        FallbackRule(String id, String name, String severity, String regex, 
                    String messageTemplate, boolean multiline) {
            this.id = id;
            this.name = name;
            this.severity = severity;
            this.pattern = Pattern.compile(regex, 
                multiline ? Pattern.MULTILINE | Pattern.CASE_INSENSITIVE : Pattern.CASE_INSENSITIVE);
            this.messageTemplate = messageTemplate;
            this.multiline = multiline;
        }
    }
    
    // High-confidence fallback rules
    private static final List<FallbackRule> FALLBACK_RULES = new ArrayList<>();
    
    static {
        // SQL Injection Vulnerabilities (Critical)
        FALLBACK_RULES.add(new FallbackRule(
            "CF_SQL_INJECTION_RISK",
            "SQL Injection Risk",
            "CRITICAL",
            "<cfquery[^>]*>.*?#(?:url|form|cgi)\\.[^#]*#.*?</cfquery>",
            "Potential SQL injection vulnerability: direct use of URL/Form/CGI variables in query",
            true
        ));
        
        // XSS Vulnerabilities (High)
        FALLBACK_RULES.add(new FallbackRule(
            "CF_XSS_OUTPUT_RISK", 
            "Cross-Site Scripting Risk",
            "HIGH",
            "#(?:url|form|cgi)\\.[^#]*#",
            "Potential XSS vulnerability: unescaped output of user input",
            false
        ));
        
        // Queries in Loops (Medium)
        FALLBACK_RULES.add(new FallbackRule(
            "CF_QUERY_IN_LOOP",
            "Query Inside Loop",
            "MEDIUM",
            "<cfloop[^>]*>.*?<cfquery[^>]*>.*?</cfquery>.*?</cfloop>",
            "Performance issue: database query inside loop can cause N+1 query problems",
            true
        ));
        
        // Hardcoded Passwords (High)
        FALLBACK_RULES.add(new FallbackRule(
            "CF_HARDCODED_PASSWORD",
            "Hardcoded Password",
            "HIGH",
            "(?:password|pwd)\\s*=\\s*[\"'][^\"']{3,}[\"']",
            "Security risk: hardcoded password found in source code",
            false
        ));
        
        // Database Connection Strings (Medium)
        FALLBACK_RULES.add(new FallbackRule(
            "CF_HARDCODED_DATASOURCE",
            "Hardcoded Database Connection",
            "MEDIUM", 
            "<cfquery[^>]*datasource\\s*=\\s*[\"'][^\"']+[\"'][^>]*>",
            "Configuration issue: hardcoded datasource should use application settings",
            false
        ));
        
        // Missing cfqueryparam (Medium)
        FALLBACK_RULES.add(new FallbackRule(
            "CF_MISSING_QUERYPARAM",
            "Missing cfqueryparam",
            "MEDIUM",
            "<cfquery[^>]*>(?:(?!<cfqueryparam|</cfquery>).)*#[^#]*#(?:(?!<cfqueryparam|</cfquery>).)*</cfquery>",
            "Security/Performance: use cfqueryparam for all dynamic SQL values",
            true
        ));
        
        // Deprecated Tags (Low)
        FALLBACK_RULES.add(new FallbackRule(
            "CF_DEPRECATED_TAGS",
            "Deprecated CFML Tags",
            "LOW",
            "<(cfinsert|cfupdate|cfgridupdate|cfgrid)\\b[^>]*>",
            "Code quality: deprecated tag usage should be updated to modern alternatives",
            false
        ));
        
        // Debug Output in Production (Medium)
        FALLBACK_RULES.add(new FallbackRule(
            "CF_DEBUG_OUTPUT",
            "Debug Output",
            "MEDIUM",
            "<(cfdump|cfabort|cftrace)\\b[^>]*>",
            "Code quality: debug/development tags should not be in production code",
            false
        ));
        
        // Complex Expressions (Low)
        FALLBACK_RULES.add(new FallbackRule(
            "CF_COMPLEX_EXPRESSION",
            "Complex Expression",
            "LOW",
            "#[^#]{80,}#",
            "Code quality: complex expressions should be broken into simpler components",
            false
        ));
        
        // Missing Error Handling (Medium)
        FALLBACK_RULES.add(new FallbackRule(
            "CF_MISSING_ERROR_HANDLING",
            "Missing Error Handling",
            "MEDIUM",
            "<cfquery[^>]*>(?:(?!<cftry|</cfquery>).)*</cfquery>",
            "Reliability: database queries should include error handling",
            true
        ));
    }
    
    private final boolean enabled;
    private final int maxIssuesPerFile;
    
    /**
     * Creates FallbackAnalyzer with default settings.
     */
    public FallbackAnalyzer() {
        this(true, 50);
    }
    
    /**
     * Creates FallbackAnalyzer with custom configuration.
     * 
     * @param enabled Whether fallback analysis is enabled
     * @param maxIssuesPerFile Maximum issues to report per file to avoid noise
     */
    public FallbackAnalyzer(boolean enabled, int maxIssuesPerFile) {
        this.enabled = enabled;
        this.maxIssuesPerFile = maxIssuesPerFile;
        
        logger.debug("FallbackAnalyzer initialized - enabled: {}, maxIssues: {}", 
                    enabled, maxIssuesPerFile);
    }
    
    /**
     * Analyzes a CFML file using regex-based fallback rules.
     * 
     * @param filePath Path to the CFML file to analyze
     * @return List of issues found by fallback analysis
     */
    public List<FallbackIssue> analyzeFile(String filePath) {
        List<FallbackIssue> issues = new ArrayList<>();
        
        if (!enabled) {
            return issues;
        }
        
        try {
            logger.debug("Running fallback analysis on file: {}", filePath);
            
            String content = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
            String[] lines = content.split("\\n");
            
            int issueCount = 0;
            
            for (FallbackRule rule : FALLBACK_RULES) {
                if (issueCount >= maxIssuesPerFile) {
                    logger.debug("Reached maximum issues per file ({}) for {}", maxIssuesPerFile, filePath);
                    break;
                }
                
                List<FallbackIssue> ruleIssues = applyRule(rule, content, lines, filePath);
                issues.addAll(ruleIssues);
                issueCount += ruleIssues.size();
            }
            
            logger.debug("Fallback analysis found {} issues in file: {}", issues.size(), filePath);
            
        } catch (IOException e) {
            logger.warn("Failed to read file for fallback analysis {}: {}", filePath, e.getMessage());
        } catch (Exception e) {
            logger.warn("Fallback analysis failed for file {}: {}", filePath, e.getMessage());
            logger.debug("Fallback analysis error details for {}: ", filePath, e);
        }
        
        return issues;
    }
    
    /**
     * Applies a single fallback rule to file content.
     * 
     * @param rule Rule to apply
     * @param content Full file content
     * @param lines Content split into lines for line number calculation
     * @param filePath File path for issue reporting
     * @return List of issues found by this rule
     */
    private List<FallbackIssue> applyRule(FallbackRule rule, String content, String[] lines, String filePath) {
        List<FallbackIssue> issues = new ArrayList<>();
        
        try {
            Matcher matcher = rule.pattern.matcher(content);
            
            while (matcher.find()) {
                int startPosition = matcher.start();
                int lineNumber = getLineNumber(content, startPosition);
                int columnNumber = getColumnNumber(content, startPosition, lineNumber);
                String evidence = matcher.group().trim();
                
                // Limit evidence length to avoid excessive output
                if (evidence.length() > 100) {
                    evidence = evidence.substring(0, 97) + "...";
                }
                
                FallbackIssue issue = new FallbackIssue(
                    rule.id,
                    rule.name,
                    rule.severity,
                    rule.messageTemplate,
                    filePath,
                    lineNumber,
                    columnNumber,
                    evidence
                );
                
                issues.add(issue);
                logger.debug("Fallback rule {} matched in {} at line {}: {}", 
                           new Object[]{rule.id, filePath, lineNumber, evidence});
            }
            
        } catch (Exception e) {
            logger.debug("Failed to apply fallback rule {} to file {}: {}", 
                        new Object[]{rule.id, filePath, e.getMessage()});
        }
        
        return issues;
    }
    
    /**
     * Calculates line number for a character position in content.
     * 
     * @param content Full content
     * @param position Character position
     * @return Line number (1-based)
     */
    private int getLineNumber(String content, int position) {
        int lineNumber = 1;
        for (int i = 0; i < position && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }
    
    /**
     * Calculates column number for a character position in content.
     * 
     * @param content Full content  
     * @param position Character position
     * @param lineNumber Line number for this position
     * @return Column number (1-based)
     */
    private int getColumnNumber(String content, int position, int lineNumber) {
        int columnNumber = 1;
        int currentLine = 1;
        
        for (int i = 0; i < position && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                currentLine++;
                columnNumber = 1;
            } else if (currentLine == lineNumber) {
                columnNumber++;
            }
        }
        
        return columnNumber;
    }
    
    /**
     * Generates XML output for fallback analysis issues in CFLint format.
     * 
     * @param issues List of issues found
     * @return XML string representation
     */
    public String generateXmlOutput(List<FallbackIssue> issues) {
        if (issues.isEmpty()) {
            return "";
        }
        
        StringBuilder xml = new StringBuilder();
        xml.append("<!-- FALLBACK_ANALYSIS_RESULTS -->\n");
        
        for (FallbackIssue issue : issues) {
            xml.append(String.format(
                "<issue severity=\"%s\" id=\"%s\" message=\"%s\" category=\"%s\" " +
                "abbrev=\"%s\">\n" +
                "  <location file=\"%s\" fileName=\"%s\" function=\"\" " +
                "column=\"%d\" line=\"%d\" message=\"%s\" variable=\"\">\n" +
                "    <Expression><![CDATA[%s]]></Expression>\n" +
                "  </location>\n" +
                "</issue>\n",
                issue.getSeverity(),
                issue.getRuleId(), 
                escapeXml(issue.getMessage()),
                "FALLBACK_ANALYSIS",
                issue.getRuleId(),
                escapeXml(issue.getFilePath()),
                escapeXml(Paths.get(issue.getFilePath()).getFileName().toString()),
                issue.getColumnNumber(),
                issue.getLineNumber(),
                escapeXml(issue.getMessage()),
                escapeXml(issue.getEvidence())
            ));
        }
        
        return xml.toString();
    }
    
    /**
     * Escapes XML special characters in text.
     * 
     * @param text Text to escape
     * @return XML-safe text
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }
    
    /**
     * Checks if fallback analysis is enabled.
     * 
     * @return true if fallback analysis is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the number of available fallback rules.
     * 
     * @return Number of fallback rules
     */
    public int getRuleCount() {
        return FALLBACK_RULES.size();
    }
}
