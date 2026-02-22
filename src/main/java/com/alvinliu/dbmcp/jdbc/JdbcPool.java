package com.alvinliu.dbmcp.jdbc;

import com.alibaba.druid.pool.DruidDataSource;
import com.alvinliu.dbmcp.config.Config;
import com.alvinliu.dbmcp.config.ConnectionEntry;
import com.alvinliu.dbmcp.core.PluginRegistry;
import com.alvinliu.dbmcp.core.SqlAnalyzer;
import com.alvinliu.dbmcp.core.SqlFormatter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds Druid connection pools by name; per-connection Analyzer/Formatter.
 * Callers must close connections obtained from {@link #getConnection(String)} (e.g. try-with-resources).
 */
public class JdbcPool {
    private final List<ConnectionEntry> configs;
    private final Map<String, DruidDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, Boolean> available = new ConcurrentHashMap<>();
    private final Map<String, SqlAnalyzer> analyzers = new ConcurrentHashMap<>();
    private final Map<String, SqlFormatter> formatters = new ConcurrentHashMap<>();

    public JdbcPool(Config config) {
        this.configs = config.getConnections();
        if (this.configs == null || this.configs.isEmpty()) {
            throw new IllegalArgumentException("at least one connection is required");
        }
        var review = config.getReview();
        var wholeText = review != null ? review.getWholeTextMatch() : null;
        var commandMatch = review != null ? review.getCommandMatch() : null;
        for (ConnectionEntry e : this.configs) {
            if (e.getName() == null || e.getName().isBlank()) continue;
            String dbType = (e.getDbType() != null && !e.getDbType().isBlank()) ? e.getDbType().trim() : "mysql";
            analyzers.put(e.getName(), PluginRegistry.getAnalyzer(dbType, wholeText, commandMatch));
            formatters.put(e.getName(), PluginRegistry.getFormatter(dbType));
            if (e.getUrl() == null || e.getUrl().isBlank()) continue;
            try {
                DruidDataSource ds = createDataSource(e);
                dataSources.put(e.getName(), ds);
                available.put(e.getName(), true);
            } catch (Exception ex) {
                System.err.println("[db_mcp] connection " + e.getName() + " failed: " + ex.getMessage());
                available.put(e.getName(), false);
            }
        }
        if (dataSources.isEmpty()) {
            throw new IllegalArgumentException("no connection could be opened");
        }
    }

    private static DruidDataSource createDataSource(ConnectionEntry e) throws SQLException {
        DruidDataSource ds = new DruidDataSource();
        if (e.getDriver() != null && !e.getDriver().isBlank()) {
            ds.setDriverClassName(e.getDriver());
        }
        ds.setUrl(e.getUrl());
        ds.setUsername(e.getUser() != null ? e.getUser() : "");
        ds.setPassword(e.getPassword() != null ? e.getPassword() : "");
        ds.setInitialSize(1);
        ds.setMaxActive(20);
        ds.setMinIdle(0);
        ds.setMaxWait(10_000);
        // validation query is DB-specific (e.g. Oracle: SELECT 1 FROM DUAL); skip to avoid driver errors
        try {
            ds.init();
        } catch (Exception ex) {
            ds.close();
            throw ex instanceof SQLException ? (SQLException) ex : new SQLException(ex.getMessage(), ex);
        }
        return ds;
    }

    public SqlAnalyzer getAnalyzer(String connectionName) {
        return analyzers.getOrDefault(connectionName, PluginRegistry.getAnalyzer("mysql",
            Collections.emptyList(), Collections.emptyList()));
    }

    public SqlFormatter getFormatter(String connectionName) {
        return formatters.getOrDefault(connectionName, PluginRegistry.getFormatter("mysql"));
    }

    /** Error message when connection is unavailable: for AI client to show to end user. */
    public static final String MSG_CONNECTION_UNAVAILABLE =
        "Database connection unavailable. Please ask the user to check the database connection; after it is available, use the list_connections tool to re-validate connectivity.";

    /**
     * Borrow a connection from the pool. Caller must close it (e.g. try-with-resources).
     * Fast-fails with a clear message if this connection is known unavailable (no retry).
     */
    public Connection getConnection(String name) throws SQLException {
        if (Boolean.FALSE.equals(available.get(name))) {
            throw new SQLException(MSG_CONNECTION_UNAVAILABLE);
        }
        DruidDataSource ds = dataSources.get(name);
        if (ds != null) return ds.getConnection();
        ConnectionEntry entry = configs.stream().filter(e -> name.equals(e.getName())).findFirst().orElse(null);
        if (entry == null) throw new SQLException("unknown connection: " + name);
        DruidDataSource newDs = createDataSource(entry);
        dataSources.put(name, newDs);
        available.put(name, true);
        return newDs.getConnection();
    }

    public List<String> getNames() {
        List<String> names = new ArrayList<>();
        for (ConnectionEntry e : configs) {
            if (e.getName() != null && !e.getName().isBlank()) names.add(e.getName());
        }
        return names;
    }

    /**
     * List all configured connections with current availability. Each call re-checks every connection
     * (getConnection + isValid). Failed or initially-unopened connections are retried (re-create pool if missing).
     */
    public List<Map<String, Object>> listConnectionsWithStatus() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ConnectionEntry e : configs) {
            String name = e.getName();
            if (name == null || name.isBlank()) continue;
            boolean ok = checkConnection(name, e);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("available", ok);
            m.put("db_type", com.alvinliu.dbmcp.core.druid.DbTypes.displayName(e.getDbType()));
            out.add(m);
        }
        return out;
    }

    /** Re-check one connection; retry creating pool if missing or failed. Returns true if available. */
    private boolean checkConnection(String name, ConnectionEntry e) {
        if (e.getUrl() == null || e.getUrl().isBlank()) return false;
        DruidDataSource ds = dataSources.get(name);
        if (ds == null) {
            try {
                ds = createDataSource(e);
                dataSources.put(name, ds);
                available.put(name, true);
                return true;
            } catch (Exception ex) {
                available.put(name, false);
                return false;
            }
        }
        try (Connection c = ds.getConnection()) {
            boolean valid = c != null && c.isValid(2);
            if (valid) available.put(name, true);
            return valid;
        } catch (SQLException ex) {
            available.put(name, false);
            try { ds.close(); } catch (Exception ignored) {}
            dataSources.remove(name);
            return false;
        }
    }

    /**
     * Returns [databaseName, schema, driver] for audit/logging. Empty strings if not set.
     */
    public String[] getConnectionMeta(String connectionName) {
        ConnectionEntry e = configs.stream().filter(c -> connectionName != null && connectionName.equals(c.getName())).findFirst().orElse(null);
        if (e == null) return new String[] { "", "", "" };
        String db = e.getDatabase() != null ? e.getDatabase().trim() : "";
        String schema = e.getSchema() != null ? e.getSchema().trim() : "";
        String driver = e.getDriver() != null ? e.getDriver().trim() : (e.getDbType() != null ? e.getDbType().trim() : "");
        return new String[] { db, schema, driver };
    }

    /**
     * Mark a connection as unavailable (e.g. after connection/execution error). Closes and removes its pool
     * so subsequent getConnection(name) fast-fails until list_connections re-checks.
     */
    public void markUnavailable(String name) {
        DruidDataSource ds = dataSources.remove(name);
        if (ds != null) {
            try { ds.close(); } catch (Exception ignored) {}
        }
        available.put(name, false);
    }

    /** True if the throwable (or its cause chain) indicates a connection failure. */
    public static boolean isConnectionError(Throwable t) {
        while (t != null) {
            String s = t.getMessage();
            if (s != null) {
                String lower = s.toLowerCase();
                if (lower.contains("ora-12541") || lower.contains("ora-12514") || lower.contains("ora-12154")
                    || lower.contains("ora-12170") || lower.contains("ora-03113") || lower.contains("ora-01012")
                    || lower.contains("ora-12560") || lower.contains("ora-17002")
                    || lower.contains("tns:") || lower.contains("no listener") || lower.contains("connection closed")
                    || lower.contains("connection reset") || lower.contains("broken pipe") || lower.contains("i/o error")
                    || lower.contains("i/o timeout") || lower.contains("driver: bad connection")
                    || lower.contains("connection refused") || lower.contains("eof")
                    || lower.contains("connection is closed") || lower.contains("connection has been closed")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    public void close() {
        for (DruidDataSource ds : dataSources.values()) {
            try { if (ds != null) ds.close(); } catch (Exception ignored) {}
        }
        dataSources.clear();
        available.clear();
    }
}
