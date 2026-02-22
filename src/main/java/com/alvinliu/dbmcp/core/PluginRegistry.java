package com.alvinliu.dbmcp.core;

import com.alibaba.druid.DbType;
import com.alvinliu.dbmcp.core.druid.DruidSqlAnalyzer;
import com.alvinliu.dbmcp.core.druid.DruidSqlFormatter;
import com.alvinliu.dbmcp.core.druid.DbTypes;

import java.util.Collections;
import java.util.List;

/**
 * Analyzer and formatter via Alibaba Druid (db_type).
 */
public final class PluginRegistry {

    public static SqlAnalyzer getAnalyzer(String dbType, List<String> dangerKeywordsWholeText,
                                          List<String> dangerKeywordsAst) {
        List<String> w = dangerKeywordsWholeText != null ? dangerKeywordsWholeText : Collections.emptyList();
        List<String> a = dangerKeywordsAst != null ? dangerKeywordsAst : Collections.emptyList();
        DbType dt = DbTypes.resolve(dbType);
        return new DruidSqlAnalyzer(dt, w, a);
    }

    public static SqlFormatter getFormatter(String dbType) {
        DbType dt = DbTypes.resolve(dbType);
        return new DruidSqlFormatter(dt);
    }
}
