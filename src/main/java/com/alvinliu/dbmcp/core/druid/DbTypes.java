package com.alvinliu.dbmcp.core.druid;

import com.alibaba.druid.DbType;

import java.util.HashMap;
import java.util.Map;

/**
 * Map config db_type string to Alibaba Druid DbType (e.g. mysql, oracle, postgresql, sql_server).
 * Druid enum names are lowercase with underscore (mysql, sql_server, etc.).
 */
public final class DbTypes {
    /** Alias config key -> Druid enum name (valueOf). Druid enum uses sqlserver not sql_server. */
    private static final Map<String, String> NAME_ALIASES = new HashMap<>();
    static {
        NAME_ALIASES.put("sql_server", "sqlserver");
        NAME_ALIASES.put("pg", "postgresql");
    }

    /**
     * Resolve config value (e.g. "mysql", "oracle") to DbType. Returns null if unknown.
     */
    public static DbType resolve(String dbType) {
        if (dbType == null || dbType.isBlank()) return null;
        String key = dbType.trim().toLowerCase().replace("-", "_");
        String enumName = NAME_ALIASES.getOrDefault(key, key);
        try {
            return DbType.valueOf(enumName);
        } catch (IllegalArgumentException e) {
            try {
                return DbType.valueOf(enumName.toUpperCase());
            } catch (IllegalArgumentException e2) {
                return null;
            }
        }
    }

    /**
     * Display name for list_connections (e.g. "mysql", "oracle"). Preserves user config or default "sql".
     */
    public static String displayName(String dbType) {
        if (dbType == null || dbType.isBlank()) return "sql";
        return dbType.trim().toLowerCase();
    }
}
