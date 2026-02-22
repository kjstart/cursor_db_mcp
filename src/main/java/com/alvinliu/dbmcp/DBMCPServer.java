package com.alvinliu.dbmcp;

import com.alvinliu.dbmcp.config.Config;
import com.alvinliu.dbmcp.config.ConfigLoader;
import com.alvinliu.dbmcp.jdbc.JdbcPool;
import com.alvinliu.dbmcp.mcp.McpServer;

import java.io.IOException;

/**
 * Entry point: load config, start MCP server on stdio.
 */
public class DBMCPServer {
    public static void main(String[] args) {
        try {
            Config config = ConfigLoader.load();
            JdbcPool pool = new JdbcPool(config);
            McpServer server = new McpServer(config, pool, System.in, System.out);
            server.run();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
