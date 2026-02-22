package com.alvinliu.dbmcp.core;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Default SQL analyzer: generic SQL only (comments, strings, tokenize, DDL/danger keyword match).
 * Plugins can extend this to add dialect-specific rules.
 */
public class BaseAnalyzer implements SqlAnalyzer {

    private static final Pattern SINGLE_LINE_COMMENT = Pattern.compile("--[^\r\n]*");
    private static final Pattern MULTI_LINE_COMMENT = Pattern.compile("/\\*[\\s\\S]*?\\*/");
    private static final List<String> DDL_KEYWORDS = Arrays.asList(
        "create", "drop", "alter", "truncate", "rename", "comment", "grant", "revoke"
    );

    private final List<String> dangerKeywords;
    private final String matchMode;

    public BaseAnalyzer(List<String> dangerKeywords, String matchMode) {
        this.dangerKeywords = dangerKeywords == null ? Collections.emptyList()
            : dangerKeywords.stream().map(s -> s == null ? "" : s.trim().toLowerCase()).collect(Collectors.toList());
        String mode = matchMode != null ? matchMode.trim().toLowerCase() : "";
        this.matchMode = "tokens".equals(mode) ? "tokens" : "whole_text";
    }

    @Override
    public AnalysisResult analyze(String sql) {
        AnalysisResult r = new AnalysisResult();
        r.setOriginalSQL(sql);
        r.setContainsPLSQL(false);
        r.setPlsqlCreationDDL(false);
        String noComments = removeComments(sql);
        String noStrings = removeStringLiterals(noComments);
        r.setNormalizedSQL(noStrings);
        r.setMultiStatement(noStrings.contains(";"));
        List<String> tokens = tokenize(noStrings);
        r.setTokens(tokens);
        r.setDdl(isDdl(tokens));
        if ("whole_text".equals(matchMode)) {
            r.setMatchedKeywords(matchKeywordsWholeText(sql, dangerKeywords));
        } else {
            r.setMatchedKeywords(matchKeywords(tokens, dangerKeywords));
        }
        r.setDangerous(r.getMatchedKeywords() != null && !r.getMatchedKeywords().isEmpty());
        r.setStatementType(getStatementType(tokens));
        return r;
    }

    static String removeComments(String sql) {
        if (sql == null) return "";
        String s = SINGLE_LINE_COMMENT.matcher(sql).replaceAll(" ");
        return MULTI_LINE_COMMENT.matcher(s).replaceAll(" ");
    }

    static String removeStringLiterals(String sql) {
        if (sql == null) return "";
        StringBuilder b = new StringBuilder();
        boolean inString = false;
        char prev = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && prev != '\'') { inString = !inString; b.append(' '); }
            else if (inString) b.append(' ');
            else b.append(c);
            prev = c;
        }
        return b.toString();
    }

    static List<String> tokenize(String sql) {
        if (sql == null) return Collections.emptyList();
        String lower = sql.toLowerCase();
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char r = lower.charAt(i);
            if (Character.isLetter(r) || Character.isDigit(r) || r == '_') cur.append(r);
            else {
                if (cur.length() > 0) { tokens.add(cur.toString()); cur.setLength(0); }
            }
        }
        if (cur.length() > 0) tokens.add(cur.toString());
        return tokens;
    }

    static boolean isDdl(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) return false;
        return DDL_KEYWORDS.contains(tokens.get(0));
    }

    static String getStatementType(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) return "UNKNOWN";
        switch (tokens.get(0)) {
            case "select": return "SELECT";
            case "insert": return "INSERT";
            case "update": return "UPDATE";
            case "delete": return "DELETE";
            case "create": return "CREATE";
            case "drop": return "DROP";
            case "alter": return "ALTER";
            case "truncate": return "TRUNCATE";
            case "grant": return "GRANT";
            case "revoke": return "REVOKE";
            case "rename": return "RENAME";
            case "comment": return "COMMENT";
            default: return tokens.get(0).toUpperCase();
        }
    }

    static List<String> matchKeywordsWholeText(String sql, List<String> keywords) {
        if (sql == null || keywords == null) return Collections.emptyList();
        String lower = sql.toLowerCase();
        List<String> matched = new ArrayList<>();
        for (String kw : keywords) {
            if (kw != null && !kw.isEmpty() && lower.contains(kw)) matched.add(kw);
        }
        return matched;
    }

    static List<String> matchKeywords(List<String> tokens, List<String> keywords) {
        if (tokens == null || keywords == null) return Collections.emptyList();
        List<String> matched = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String kw : keywords) {
            if (kw == null || kw.isEmpty() || seen.contains(kw)) continue;
            List<String> kwTokens = tokenize(kw);
            if (kwTokens.isEmpty()) continue;
            if (kwTokens.size() == 1) {
                if (tokens.contains(kwTokens.get(0))) { matched.add(kw); seen.add(kw); }
            } else {
                if (matchConsecutiveTokens(tokens, kwTokens)) { matched.add(kw); seen.add(kw); }
            }
        }
        return matched;
    }

    static boolean matchConsecutiveTokens(List<String> tokens, List<String> kwTokens) {
        if (kwTokens.size() > tokens.size()) return false;
        for (int i = 0; i <= tokens.size() - kwTokens.size(); i++) {
            boolean match = true;
            for (int j = 0; j < kwTokens.size(); j++) {
                if (!tokens.get(i + j).equals(kwTokens.get(j))) { match = false; break; }
            }
            if (match) return true;
        }
        return false;
    }
}
