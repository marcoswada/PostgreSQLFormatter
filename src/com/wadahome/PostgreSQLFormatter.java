package com.wadahome;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostgreSQLFormatter {
    
    public enum CaseStyle {
        UPPERCASE,
        LOWERCASE,
        CAPITALIZE
    }
    
    public static class FormatterConfig {
        private CaseStyle reservedWordCase = CaseStyle.UPPERCASE;
        private CaseStyle objectCase = CaseStyle.LOWERCASE;
        private CaseStyle functionCase = CaseStyle.LOWERCASE;
        private int indentSize = 4;
        private int baseIndent = 0;
        private String indentChar = " ";
        
        public FormatterConfig setReservedWordCase(CaseStyle caseStyle) {
            this.reservedWordCase = caseStyle;
            return this;
        }
        
        public FormatterConfig setObjectCase(CaseStyle caseStyle) {
            this.objectCase = caseStyle;
            return this;
        }
        
        public FormatterConfig setFunctionCase(CaseStyle caseStyle) {
            this.functionCase = caseStyle;
            return this;
        }
        
        public FormatterConfig setIndentSize(int size) {
            this.indentSize = size;
            return this;
        }
        
        public FormatterConfig setBaseIndent(int indent) {
            this.baseIndent = indent;
            return this;
        }
        
        public FormatterConfig setIndentChar(String indentChar) {
            this.indentChar = indentChar;
            return this;
        }
        
        // Getters
        public CaseStyle getReservedWordCase() { return reservedWordCase; }
        public CaseStyle getObjectCase() { return objectCase; }
        public CaseStyle getFunctionCase() { return functionCase; }
        public int getIndentSize() { return indentSize; }
        public int getBaseIndent() { return baseIndent; }
        public String getIndentChar() { return indentChar; }
    }
    
    private static final Set<String> RESERVED_WORDS = new HashSet<>(Arrays.asList(
        "SELECT", "FROM", "WHERE", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER",
        "ON", "AND", "OR", "NOT", "IN", "EXISTS", "BETWEEN", "LIKE", "IS", "NULL",
        "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE",
        "ALTER", "DROP", "INDEX", "VIEW", "DISTINCT", "ORDER", "BY", "GROUP",
        "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "AS", "CASE", "WHEN", "THEN",
        "ELSE", "END", "IF", "WITH", "RECURSIVE", "WINDOW", "OVER", "PARTITION",
        "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "CHECK", "DEFAULT",
        "CONSTRAINT", "DATABASE", "SCHEMA", "GRANT", "REVOKE", "COMMIT", "ROLLBACK",
        "TRANSACTION", "BEGIN", "START", "SAVEPOINT", "RELEASE"
    ));
    
    private static final Set<String> FUNCTIONS = new HashSet<>(Arrays.asList(
        "COUNT", "SUM", "AVG", "MIN", "MAX", "COALESCE", "NULLIF", "GREATEST",
        "LEAST", "CONCAT", "LENGTH", "SUBSTR", "SUBSTRING", "UPPER", "LOWER",
        "TRIM", "LTRIM", "RTRIM", "REPLACE", "SPLIT_PART", "POSITION", "NOW",
        "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "EXTRACT", "DATE_PART",
        "AGE", "TO_CHAR", "TO_DATE", "TO_TIMESTAMP", "CAST", "CONVERT", "ROUND",
        "CEIL", "FLOOR", "ABS", "POWER", "SQRT", "MOD", "RANDOM", "GENERATE_SERIES"
    ));
    
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b");
    private static final Pattern STRING_PATTERN = Pattern.compile("'([^'\\\\]|\\\\.)*'");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("--.*$|/\\*[\\s\\S]*?\\*/", Pattern.MULTILINE);
    
    private final FormatterConfig config;
    
    public PostgreSQLFormatter(FormatterConfig config) {
        this.config = config;
    }
    
    public PostgreSQLFormatter() {
        this(new FormatterConfig());
    }
    
    public String format(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        
        // Store literals and comments to preserve them
        Map<String, String> preservedStrings = new HashMap<>();
        String processedSql = preserveLiterals(sql, preservedStrings);
        
        // Clean up whitespace
        processedSql = cleanWhitespace(processedSql);
        
        // Apply case transformations
        processedSql = applyCaseTransformations(processedSql);
        
        // Format structure and indentation
        processedSql = formatStructure(processedSql);
        
        // Restore preserved strings
        processedSql = restoreLiterals(processedSql, preservedStrings);
        
        return processedSql;
    }
    
    private String preserveLiterals(String sql, Map<String, String> preserved) {
        String result = sql;
        int counter = 0;
        
        // Preserve string literals
        Matcher stringMatcher = STRING_PATTERN.matcher(result);
        while (stringMatcher.find()) {
            String placeholder = "___STRING_" + (counter++) + "___";
            preserved.put(placeholder, stringMatcher.group());
            result = result.replace(stringMatcher.group(), placeholder);
            stringMatcher = STRING_PATTERN.matcher(result);
        }
        
        // Preserve quoted identifiers
        Matcher identifierMatcher = IDENTIFIER_PATTERN.matcher(result);
        while (identifierMatcher.find()) {
            String placeholder = "___IDENTIFIER_" + (counter++) + "___";
            preserved.put(placeholder, identifierMatcher.group());
            result = result.replace(identifierMatcher.group(), placeholder);
            identifierMatcher = IDENTIFIER_PATTERN.matcher(result);
        }
        
        // Preserve comments
        Matcher commentMatcher = COMMENT_PATTERN.matcher(result);
        while (commentMatcher.find()) {
            String placeholder = "___COMMENT_" + (counter++) + "___";
            preserved.put(placeholder, commentMatcher.group());
            result = result.replace(commentMatcher.group(), placeholder);
            commentMatcher = COMMENT_PATTERN.matcher(result);
        }
        
        return result;
    }
    
    private String restoreLiterals(String sql, Map<String, String> preserved) {
        String result = sql;
        for (Map.Entry<String, String> entry : preserved.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
    
    private String cleanWhitespace(String sql) {
        // Remove excessive whitespace while preserving single spaces
        return sql.replaceAll("\\s+", " ").trim();
    }
    
    private String applyCaseTransformations(String sql) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = WORD_PATTERN.matcher(sql);
        int lastEnd = 0;
        
        while (matcher.find()) {
            result.append(sql.substring(lastEnd, matcher.start()));
            String word = matcher.group();
            String upperWord = word.toUpperCase();
            
            if (RESERVED_WORDS.contains(upperWord)) {
                result.append(applyCase(word, config.getReservedWordCase()));
            } else if (FUNCTIONS.contains(upperWord)) {
                result.append(applyCase(word, config.getFunctionCase()));
            } else {
                result.append(applyCase(word, config.getObjectCase()));
            }
            
            lastEnd = matcher.end();
        }
        result.append(sql.substring(lastEnd));
        
        return result.toString();
    }
    
    private String applyCase(String word, CaseStyle caseStyle) {
        switch (caseStyle) {
            case UPPERCASE:
                return word.toUpperCase();
            case LOWERCASE:
                return word.toLowerCase();
            case CAPITALIZE:
                return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
            default:
                return word;
        }
    }
    
    private String formatStructure(String sql) {
        String[] tokens = tokenize(sql);
        StringBuilder result = new StringBuilder();
        int indentLevel = 0;
        boolean newLine = true;
        boolean inSelect = false;
        boolean inFrom = false;
        boolean afterSelect = false;
        
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            String upperToken = token.toUpperCase();
//            String nextToken = i + 1 < tokens.length ? tokens[i + 1].toUpperCase() : "";
            
            // Handle major keywords that start new clauses
            if (isMajorKeyword(upperToken)) {
                if (!newLine && result.length() > 0) {
                    result.append("\n");
                }
                result.append(getIndent(indentLevel));
                result.append(token);
                
                if (upperToken.equals("SELECT")) {
                    inSelect = true;
                    afterSelect = true;
                } else {
                    inSelect = false;
                    afterSelect = false;
                }
                
                if (upperToken.equals("FROM")) {
                    inFrom = true;
                } else if (isMajorKeyword(upperToken) && !upperToken.equals("FROM")) {
                    inFrom = false;
                }
                
                newLine = false;
            }
            // Handle SELECT list items
            else if (inSelect && token.equals(",")) {
                result.append(",\n");
                result.append(getIndent(indentLevel + 1));
                newLine = false;
                afterSelect = false;
            }
            // Handle JOIN keywords
            else if (isJoinKeyword(upperToken) || (upperToken.equals("ON") && inFrom)) {
                if (!newLine) {
                    result.append("\n");
                }
                result.append(getIndent(indentLevel + 1));
                result.append(token);
                newLine = false;
            }
            // Handle parentheses
            else if (token.equals("(")) {
                if (!newLine && !result.toString().trim().endsWith("(")) {
                    result.append(" ");
                }
                result.append(token);
                indentLevel++;
                newLine = false;
            }
            else if (token.equals(")")) {
                indentLevel = Math.max(0, indentLevel - 1);
                result.append(token);
                newLine = false;
            }
            // Handle regular tokens
            else {
                if (!newLine && !token.equals(",") && !token.equals(")") && 
                    !result.toString().endsWith("(") && !result.toString().endsWith(" ")) {
                    result.append(" ");
                }
                
                // Special handling for first item after SELECT
                if (afterSelect && !upperToken.equals("DISTINCT") && !upperToken.equals("ALL")) {
                    if (inSelect) {
                        result.append("\n");
                        result.append(getIndent(indentLevel + 1));
                    }
                    afterSelect = false;
                }
                
                result.append(token);
                newLine = false;
            }
        }
        
        return result.toString().trim();
    }
    
    private String[] tokenize(String sql) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;
        
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            
            if (!inQuotes && (c == '\'' || c == '"')) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                inQuotes = true;
                quoteChar = c;
                current.append(c);
            } else if (inQuotes && c == quoteChar) {
                current.append(c);
                tokens.add(current.toString());
                current.setLength(0);
                inQuotes = false;
            } else if (inQuotes) {
                current.append(c);
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else if (c == '(' || c == ')' || c == ',' || c == ';') {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        
        return tokens.toArray(new String[0]);
    }
    
    private boolean isMajorKeyword(String token) {
        return Arrays.asList("SELECT", "FROM", "WHERE", "GROUP", "ORDER", "HAVING", 
                           "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "DROP",
                           "WITH", "UNION", "EXCEPT", "INTERSECT").contains(token);
    }
    
    private boolean isJoinKeyword(String token) {
        return Arrays.asList("JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS").contains(token);
    }
    
    private String getIndent(int level) {
        int totalIndent = config.getBaseIndent() + (level * config.getIndentSize());
        return String.join("", Collections.nCopies(totalIndent, config.getIndentChar()));
    }
    
    // Usage example and testing
    public static void main(String[] args) {
        // Example usage with different configurations
        String sampleSQL = "select u.id, u.name, p.title from users u left join posts p on u.id = p.user_id where u.active = true and p.published_at > '2023-01-01' order by u.name, p.created_at desc;";
        
        System.out.println("Original SQL:");
        System.out.println(sampleSQL);
        System.out.println();
        
        // Default configuration
        PostgreSQLFormatter defaultFormatter = new PostgreSQLFormatter();
        System.out.println("Default formatting:");
        System.out.println(defaultFormatter.format(sampleSQL));
        System.out.println();
        
        // Custom configuration
        FormatterConfig customConfig = new FormatterConfig()
            .setReservedWordCase(CaseStyle.UPPERCASE)
            .setObjectCase(CaseStyle.LOWERCASE)
            .setFunctionCase(CaseStyle.CAPITALIZE)
            .setIndentSize(2)
            .setBaseIndent(4);
            
        PostgreSQLFormatter customFormatter = new PostgreSQLFormatter(customConfig);
        System.out.println("Custom formatting (base indent 4, indent size 2):");
        System.out.println(customFormatter.format(sampleSQL));
        System.out.println();
        
        // Complex query example
        String complexSQL = "WITH RECURSIVE employee_hierarchy AS (    SELECT id, name, manager_id, 0 as level    FROM employees    WHERE manager_id IS NULL    UNION ALL    SELECT e.id, e.name, e.manager_id, eh.level + 1    FROM employees e    INNER JOIN employee_hierarchy eh ON e.manager_id = eh.id) SELECT eh.level, eh.name, COUNT(sub.id) as subordinates FROM employee_hierarchy eh LEFT JOIN employee_hierarchy sub ON eh.id = sub.manager_id GROUP BY eh.level, eh.name HAVING COUNT(sub.id) > 0 ORDER BY eh.level, eh.name;";
        
        System.out.println("Complex query formatting:");
        System.out.println(customFormatter.format(complexSQL));
    }
}