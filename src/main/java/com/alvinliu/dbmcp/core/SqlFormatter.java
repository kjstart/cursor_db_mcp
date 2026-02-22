package com.alvinliu.dbmcp.core;

/**
 * SQL formatter for display (e.g. confirmation dialog).
 * Plugins implement this; core provides {@link BaseFormatter} when no plugin is found.
 * {@link #formatHtml(String)} is used for the confirm window (syntax-highlighted HTML per dialect).
 */
public interface SqlFormatter {
    /**
     * Format SQL for human-readable plain text (indentation, keyword case).
     */
    String format(String sql);

    /**
     * Format SQL as a full HTML document with syntax highlighting (keywords, strings, comments, numbers).
     * Used by the confirmation dialog. Default: escaped SQL in &lt;pre&gt;; override (e.g. in BaseFormatter) for real highlighting.
     */
    String formatHtml(String sql);

    /**
     * Syntax-highlight the given SQL as HTML without changing its layout (no format/pretty-print).
     * Used when preview must show exact text (e.g. parse failed = original; parse success = already formatted by Druid).
     */
    default String formatHtmlPreserveLayout(String sql) {
        return formatHtml(sql);
    }
}
