package com.alvinliu.dbmcp;

import com.alvinliu.dbmcp.config.Config;
import com.alvinliu.dbmcp.config.ConfigLoader;
import com.alvinliu.dbmcp.jdbc.JdbcPool;
import com.alvinliu.dbmcp.mcp.McpServer;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point: load config, start MCP server on stdio.
 */
public class DBMCPServer {
    public static void main(String[] args) {
        try {
            // Suppress noisy Druid connection error logs; DBMCP prints its own concise messages.
            Logger druidLogger = Logger.getLogger("com.alibaba.druid.pool.DruidDataSource");
            druidLogger.setLevel(Level.OFF);

            Config config = ConfigLoader.load();
            JdbcPool pool = new JdbcPool(config);
            McpServer server = new McpServer(config, pool, System.in, System.out);
            server.run();
        } catch (IOException e) {
            // Fatal I/O error: log to stderr for debugging, then exit.
            System.err.println("[db_mcp] FATAL: " + e.getMessage());
            System.exit(1);
        }
    }
}
