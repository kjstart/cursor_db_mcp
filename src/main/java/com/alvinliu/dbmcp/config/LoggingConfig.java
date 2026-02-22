package com.alvinliu.dbmcp.config;

/**
 * Logging settings: no defaults; omit section to disable.
 */
public class LoggingConfig {
    private boolean auditLog = false;
    private boolean mcpConsoleLog = false;
    private String logFile = "";

    public boolean isAuditLog() { return auditLog; }
    public void setAuditLog(boolean auditLog) { this.auditLog = auditLog; }

    public boolean isMcpConsoleLog() { return mcpConsoleLog; }
    public void setMcpConsoleLog(boolean mcpConsoleLog) { this.mcpConsoleLog = mcpConsoleLog; }

    public String getLogFile() { return logFile; }
    public void setLogFile(String logFile) { this.logFile = logFile != null ? logFile : ""; }
}
