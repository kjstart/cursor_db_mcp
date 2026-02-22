package com.alvinliu.dbmcp.jdbc;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Execute SQL via JDBC and return ExecutionResult. Splits by semicolon for multiple statements.
 */
public final class JdbcExecutor {

    public static ExecutionResult execute(Connection conn, String sql) {
        ExecutionResult result = new ExecutionResult();
        long start = System.currentTimeMillis();
        sql = sql.trim();
        if (sql.isEmpty()) {
            result.setSuccess(false);
            result.setStatementType("UNKNOWN");
            result.setWarning("empty SQL");
            result.setExecutionTimeMs(System.currentTimeMillis() - start);
            return result;
        }
        String[] statements = isPlsqlDdl(sql) ? new String[] { sql } : splitStatements(sql);
        ExecutionResult last = null;
        for (String stmt : statements) {
            stmt = stmt.trim();
            if (stmt.isEmpty()) continue;
            last = executeOne(conn, stmt);
            last.setExecutionTimeMs(System.currentTimeMillis() - start);
        }
        if (last != null) {
            result.setColumns(last.getColumns());
            result.setRows(last.getRows());
            result.setRowsAffected(last.getRowsAffected());
            result.setSuccess(last.isSuccess());
            result.setStatementType(last.getStatementType());
            result.setWarning(last.getWarning());
            result.setExecutionTimeMs(last.getExecutionTimeMs());
        } else {
            result.setSuccess(true);
            result.setStatementType(inferStatementType(sql));
        }
        result.setExecutionTimeMs(System.currentTimeMillis() - start);
        return result;
    }

    /** True if SQL is PL/SQL DDL (CREATE FUNCTION/PROCEDURE/PACKAGE) and must be run as one statement. */
    private static boolean isPlsqlDdl(String sql) {
        String u = sql.trim().toUpperCase();
        if (!u.startsWith("CREATE")) return false;
        return u.contains(" FUNCTION ") || u.contains(" PROCEDURE ") || u.contains(" PACKAGE ");
    }

    /**
     * Split SQL by semicolon, but do not split on semicolons inside single-quoted strings
     * (so PL/SQL blocks and DDL like CREATE FUNCTION work as one statement).
     */
    private static String[] splitStatements(String sql) {
        List<String> list = new ArrayList<>();
        int start = 0;
        int len = sql.length();
        int i = 0;
        while (start < len) {
            boolean inSingle = false;
            int semi = -1;
            for (i = start; i < len; i++) {
                char c = sql.charAt(i);
                if (c == '\'') {
                    if (inSingle && i + 1 < len && sql.charAt(i + 1) == '\'') {
                        i++; // skip escaped quote
                    } else {
                        inSingle = !inSingle;
                    }
                } else if (!inSingle && c == ';') {
                    semi = i;
                    break;
                }
            }
            if (semi < 0) {
                list.add(sql.substring(start).trim());
                break;
            }
            list.add(sql.substring(start, semi).trim());
            start = semi + 1;
        }
        return list.toArray(new String[0]);
    }

    private static ExecutionResult executeOne(Connection conn, String sql) {
        ExecutionResult r = new ExecutionResult();
        r.setStatementType(inferStatementType(sql));
        try {
            try (Statement st = conn.createStatement()) {
                st.setQueryTimeout(300);
                boolean isResultSet = st.execute(sql);
                if (isResultSet) {
                    try (ResultSet rs = st.getResultSet()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int cols = meta.getColumnCount();
                        List<String> columnNames = new ArrayList<>();
                        for (int i = 1; i <= cols; i++) {
                            columnNames.add(meta.getColumnLabel(i));
                        }
                        r.setColumns(columnNames);
                        List<List<Object>> rows = new ArrayList<>();
                        while (rs.next()) {
                            List<Object> row = new ArrayList<>();
                            for (int i = 1; i <= cols; i++) {
                                Object v = rs.getObject(i);
                                row.add(v instanceof Clob ? clobToString((Clob) v) : v);
                            }
                            rows.add(row);
                        }
                        r.setRows(rows);
                        r.setRowsAffected(rows.size());
                    }
                } else {
                    r.setRowsAffected(st.getUpdateCount() >= 0 ? st.getUpdateCount() : 0);
                }
                r.setSuccess(true);
            }
        } catch (SQLException e) {
            r.setSuccess(false);
            r.setWarning(e.getMessage());
        }
        return r;
    }

    private static String clobToString(Clob clob) throws SQLException {
        if (clob == null) return null;
        try (Reader r = clob.getCharacterStream()) {
            if (r == null) return null;
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) >= 0) sb.append(buf, 0, n);
            return sb.toString();
        } catch (IOException e) {
            throw new SQLException("Failed to read CLOB", e);
        }
    }

    /**
     * Execute SQL and write the result to a file as CSV (header + rows). Uses UTF-8.
     * Returns the number of rows written. For non–result-set statements writes "Rows affected: N".
     */
    public static long executeToCsvFile(Connection conn, String sql, Path filePath) throws SQLException, IOException {
        ExecutionResult r = execute(conn, sql);
        if (!r.isSuccess()) {
            throw new SQLException(r.getWarning() != null ? r.getWarning() : "Execution failed");
        }
        long rowsWritten;
        try (BufferedWriter w = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            if (r.getColumns() != null && r.getRows() != null) {
                w.write(csvEscapeRow(r.getColumns()));
                w.newLine();
                for (List<Object> row : r.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (Object o : row) cells.add(o == null ? "" : o.toString());
                    w.write(csvEscapeRow(cells));
                    w.newLine();
                }
                rowsWritten = r.getRows().size();
            } else {
                w.write("Rows affected: " + r.getRowsAffected());
                w.newLine();
                rowsWritten = r.getRowsAffected();
            }
        }
        return rowsWritten;
    }

    /**
     * Execute SQL and write the result to a file as plain text: no header, columns tab-separated per row.
     * No extra newlines added between rows; only newlines present in the cell data are written (e.g. CLOB with line breaks).
     * CLOB columns are read in full and written as text. Uses UTF-8. Returns the number of rows written.
     */
    public static long executeToTextFile(Connection conn, String sql, Path filePath) throws SQLException, IOException {
        ExecutionResult r = execute(conn, sql);
        if (!r.isSuccess()) {
            throw new SQLException(r.getWarning() != null ? r.getWarning() : "Execution failed");
        }
        long rowsWritten;
        try (BufferedWriter w = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            if (r.getColumns() != null && r.getRows() != null) {
                for (List<Object> row : r.getRows()) {
                    for (int i = 0; i < row.size(); i++) {
                        if (i > 0) w.write('\t');
                        Object o = row.get(i);
                        if (o != null) w.write(o.toString());
                    }
                    // do not add newline between rows; only data's own newlines appear
                }
                rowsWritten = r.getRows().size();
            } else {
                w.write("Rows affected: " + r.getRowsAffected());
                w.newLine();
                rowsWritten = r.getRowsAffected();
            }
        }
        return rowsWritten;
    }

    /** CSV standard: quote field if it contains comma, double-quote, newline, or CR; escape " as "". */
    private static String csvEscapeRow(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            String s = cells.get(i) == null ? "" : cells.get(i);
            if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                sb.append('"').append(s.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(s);
            }
        }
        return sb.toString();
    }

    private static String inferStatementType(String sql) {
        String upper = sql.toUpperCase().trim();
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        if (upper.startsWith("MERGE")) return "MERGE";
        if (upper.startsWith("CREATE")) return "DDL";
        if (upper.startsWith("ALTER")) return "DDL";
        if (upper.startsWith("DROP")) return "DDL";
        if (upper.startsWith("TRUNCATE")) return "DDL";
        return "UNKNOWN";
    }
}
