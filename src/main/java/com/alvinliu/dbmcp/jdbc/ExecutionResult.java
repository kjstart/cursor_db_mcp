package com.alvinliu.dbmcp.jdbc;

import java.util.List;

/**
 * Result of executing SQL (aligned with Go ExecutionResult for MCP response).
 */
public class ExecutionResult {
    private List<String> columns;
    private List<List<Object>> rows;
    private long rowsAffected;
    private boolean success;
    private String statementType;
    private long executionTimeMs;
    private String warning;

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }

    public List<List<Object>> getRows() { return rows; }
    public void setRows(List<List<Object>> rows) { this.rows = rows; }

    public long getRowsAffected() { return rowsAffected; }
    public void setRowsAffected(long rowsAffected) { this.rowsAffected = rowsAffected; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getStatementType() { return statementType; }
    public void setStatementType(String statementType) { this.statementType = statementType; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }
}
