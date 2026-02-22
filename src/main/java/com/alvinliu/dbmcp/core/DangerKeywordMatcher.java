package com.alvinliu.dbmcp.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Review keyword match: plain string comparison only, no AST.
 * Normalize whitespace (including newlines) to spaces before matching; last gate.
 */
public final class DangerKeywordMatcher {

    private DangerKeywordMatcher() {}

    /**
     * Whole-text mode: normalize all whitespace in SQL to single space, lowercase, then substring match.
     * Text only, no parse, no AST.
     */
    public static List<String> matchWholeText(String sql, List<String> keywords) {
        if (sql == null || keywords == null || keywords.isEmpty()) return Collections.emptyList();
        String normalized = normalizeWhitespace(sql);
        if (normalized.isEmpty()) return Collections.emptyList();
        String lower = normalized.toLowerCase();
        List<String> matched = new ArrayList<>();
        for (String kw : keywords) {
            if (kw == null) continue;
            String k = kw.trim();
            if (k.isEmpty()) continue;
            String kLower = k.toLowerCase();
            if (lower.contains(kLower)) matched.add(k);
        }
        return matched;
    }

    /** Replace all runs of whitespace (including \\r \\n \\t) with single space and trim. */
    public static String normalizeWhitespace(String sql) {
        if (sql == null) return "";
        StringBuilder sb = new StringBuilder(sql.length());
        boolean wasSpace = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                if (!wasSpace) sb.append(' ');
                wasSpace = true;
            } else {
                sb.append(c);
                wasSpace = false;
            }
        }
        return sb.toString().trim();
    }
}
