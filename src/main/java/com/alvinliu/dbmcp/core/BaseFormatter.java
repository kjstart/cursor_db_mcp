package com.alvinliu.dbmcp.core;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Default SQL formatter: generic keywords and simple indentation.
 * formatHtml() produces HTML with syntax highlighting using this formatter's keyword set (dialect-specific when subclassed, e.g. OracleFormatter).
 */
public class BaseFormatter implements SqlFormatter {

    private static final String HTML_HEAD = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>\n"
        + ".sql-wrap { font-family: Consolas, monospace; font-size: 11pt; background: #ffffff; color: #24292e; padding: 12px; white-space: pre-wrap; word-break: break-word; overflow: visible; margin: 0; }\n"
        + ".sql-wrap .kw { color: #0550ae; }\n"
        + ".sql-wrap .str { color: #cf2222; }\n"
        + ".sql-wrap .cm { color: #57606a; }\n"
        + ".sql-wrap .num { color: #116329; }\n"
        + ".sql-wrap .id { color: #953800; }\n"
        + "</style></head><body class=\"sql-wrap\"><code>";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    protected static final Set<String> STANDARD_KEYWORDS = new HashSet<>(Arrays.asList(
        "select", "from", "where", "and", "or", "not", "insert", "into", "values", "update", "set",
        "delete", "create", "drop", "alter", "table", "add", "modify", "column",
        "index", "view", "join", "left", "right", "inner", "outer", "on", "group", "order",
        "by", "having", "limit", "offset", "as", "asc", "desc", "null", "truncate", "grant",
        "revoke", "rename", "comment",
        "number", "varchar", "varchar2", "char", "date", "clob", "blob", "int", "integer", "float", "decimal"
    ));
    protected static final Set<String> STANDARD_CLAUSE_START = new HashSet<>(Arrays.asList(
        "from", "where", "and", "or", "join", "left", "right", "inner", "outer",
        "group", "order", "having", "limit", "offset", "set", "into", "values"
    ));

    private final Set<String> keywords;
    private final Set<String> clauseStart;

    public BaseFormatter() {
        this(STANDARD_KEYWORDS, STANDARD_CLAUSE_START);
    }

    protected BaseFormatter(Set<String> keywords, Set<String> clauseStart) {
        this.keywords = keywords != null ? keywords : STANDARD_KEYWORDS;
        this.clauseStart = clauseStart != null ? clauseStart : STANDARD_CLAUSE_START;
    }

    @Override
    public String format(String sql) {
        if (sql == null || sql.isBlank()) return "";
        sql = WHITESPACE.matcher(sql.trim()).replaceAll(" ");
        List<String> parts = tokenizeForFormat(sql);
        StringBuilder b = new StringBuilder();
        int indent = 0;
        for (int i = 0; i < parts.size(); i++) {
            String p = parts.get(i);
            String lower = p.toLowerCase();
            if (i > 0) b.append(' ');
            if (keywords.contains(lower)) {
                if (clauseStart.contains(lower) && b.length() > 0) {
                    b.append('\n');
                    for (int j = 0; j < indent; j++) b.append("  ");
                }
                b.append(p.toUpperCase());
            } else {
                b.append(p);
            }
        }
        return b.toString().trim();
    }

    static List<String> tokenizeForFormat(String sql) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < sql.length(); i++) {
            char r = sql.charAt(i);
            if (r == ' ' || r == '\t' || r == '\n' || r == '\r') {
                if (cur.length() > 0) { tokens.add(cur.toString()); cur.setLength(0); }
                continue;
            }
            if (isSymbol(r)) {
                if (cur.length() > 0) { tokens.add(cur.toString()); cur.setLength(0); }
                tokens.add(String.valueOf(r));
                continue;
            }
            cur.append(r);
        }
        if (cur.length() > 0) tokens.add(cur.toString());
        return tokens;
    }

    static boolean isSymbol(char r) {
        return r == ',' || r == '(' || r == ')' || r == ';' || r == '.' || r == '=' || r == '*' || r == '\'' || r == '"';
    }

    /**
     * HTML with syntax highlighting for confirm window. Uses this formatter's keyword set (dialect-specific in subclasses).
     */
    @Override
    public String formatHtml(String sql) {
        if (sql == null || sql.isEmpty()) return HTML_HEAD + "</code></body></html>";
        sql = sql.replace("\r\n", "\n").replace("\r", "\n");
        StringBuilder out = new StringBuilder(HTML_HEAD);
        int i = 0;
        final int len = sql.length();
        while (i < len) {
            // Double-quoted identifier (e.g. "create") — do not treat content as keyword
            if (sql.charAt(i) == '"') {
                int start = i;
                i++;
                while (i < len) {
                    if (sql.charAt(i) == '"') {
                        i++;
                        if (i < len && sql.charAt(i) == '"') { i++; continue; }
                        break;
                    }
                    i++;
                }
                appendSpan(out, "id", sql.substring(start, i));
                continue;
            }
            // String literal (single-quoted, '' is escaped)
            if (sql.charAt(i) == '\'') {
                int start = i;
                i++;
                while (i < len) {
                    if (sql.charAt(i) == '\'') {
                        i++;
                        if (i < len && sql.charAt(i) == '\'') { i++; continue; }
                        break;
                    }
                    i++;
                }
                appendSpan(out, "str", sql.substring(start, i));
                continue;
            }
            // Line comment --
            if (i + 1 < len && sql.charAt(i) == '-' && sql.charAt(i + 1) == '-') {
                int start = i;
                while (i < len && sql.charAt(i) != '\n') i++;
                appendSpan(out, "cm", sql.substring(start, i));
                continue;
            }
            // Block comment /* */
            if (i + 1 < len && sql.charAt(i) == '/' && sql.charAt(i + 1) == '*') {
                int start = i;
                i += 2;
                while (i + 1 < len && (sql.charAt(i) != '*' || sql.charAt(i + 1) != '/')) i++;
                if (i + 1 < len) i += 2;
                appendSpan(out, "cm", sql.substring(start, i));
                continue;
            }
            // Identifier or number
            if (Character.isLetter(sql.charAt(i)) || sql.charAt(i) == '_' || Character.isDigit(sql.charAt(i))) {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) i++;
                String seg = sql.substring(start, i);
                boolean allDigits = seg.length() > 0 && seg.chars().allMatch(Character::isDigit);
                if (allDigits) {
                    appendSpan(out, "num", seg);
                } else if (keywords.contains(seg.toLowerCase())) {
                    appendSpan(out, "kw", seg);
                } else {
                    out.append(escapeHtml(seg));
                }
                continue;
            }
            out.append(escapeHtml(String.valueOf(sql.charAt(i))));
            i++;
        }
        out.append("</code></body></html>");
        return out.toString();
    }

    private static void appendSpan(StringBuilder out, String cssClass, String text) {
        out.append("<span class=\"").append(cssClass).append("\">").append(escapeHtml(text)).append("</span>");
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\n", "<br>").replace(" ", "&nbsp;");
    }
}
