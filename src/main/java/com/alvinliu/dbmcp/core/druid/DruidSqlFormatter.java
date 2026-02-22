package com.alvinliu.dbmcp.core.druid;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alvinliu.dbmcp.core.BaseFormatter;
import com.alvinliu.dbmcp.core.SqlFormatter;

import java.util.List;

/**
 * SQL formatter using Druid SQLUtils.toSQLString; fallback to BaseFormatter on parse failure.
 * formatHtml uses BaseFormatter highlighter (Druid AST highlighter can be added later).
 */
public class DruidSqlFormatter implements SqlFormatter {

    private final DbType dbType;
    private final BaseFormatter fallback = new BaseFormatter();

    public DruidSqlFormatter(DbType dbType) {
        this.dbType = dbType != null ? dbType : DbType.mysql;
    }

    @Override
    public String format(String sql) {
        if (sql == null || sql.isBlank()) return "";
        String trimmed = sql.trim();
        try {
            List<SQLStatement> stmts = SQLUtils.parseStatements(trimmed, dbType);
            if (stmts == null || stmts.isEmpty()) return fallback.format(sql);
            return SQLUtils.toSQLString(stmts, dbType).trim();
        } catch (Exception e) {
            return fallback.format(sql);
        }
    }

    @Override
    public String formatHtml(String sql) {
        String formatted = format(sql);
        String toHighlight = formatted != null ? formatted : (sql != null ? sql : "");
        return fallback.formatHtml(toHighlight);
    }

    /** Syntax highlight only; do not change layout (no format). */
    @Override
    public String formatHtmlPreserveLayout(String sql) {
        return fallback.formatHtml(sql != null ? sql : "");
    }
}
