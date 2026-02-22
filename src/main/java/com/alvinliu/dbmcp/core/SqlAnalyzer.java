package com.alvinliu.dbmcp.core;

/**
 * SQL analyzer: danger keywords, DDL detection, statement type.
 * Core uses {@link com.alvinliu.dbmcp.core.druid.DruidSqlAnalyzer} (Druid parse + whole_text/command_match).
 */
public interface SqlAnalyzer {
    /**
     * Analyze SQL and return matched keywords, DDL flag, statement type, etc.
     */
    AnalysisResult analyze(String sql);
}
