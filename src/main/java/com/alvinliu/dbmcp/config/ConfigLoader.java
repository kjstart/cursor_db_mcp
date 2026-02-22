package com.alvinliu.dbmcp.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Load config from config.yaml (current dir or DB_MCP_CONFIG env).
 */
public final class ConfigLoader {
    private static final String CONFIG_ENV = "DB_MCP_CONFIG";

    public static Config load() throws IOException {
        Path path = findConfigPath();
        if (path == null) {
            throw new IOException("config file not found: create config.yaml or set " + CONFIG_ENV);
        }
        return loadFromFile(path);
    }

    @SuppressWarnings("unchecked")
    public static Config loadFromFile(Path path) throws IOException {
        Yaml yaml = new Yaml();
        String content = Files.readString(path);
        Map<String, Object> raw = yaml.load(content);
        Config cfg = new Config();
        cfg.setConfigPath(path.toAbsolutePath().toString());
        if (raw == null) return cfg;
        Object conns = raw.get("connections");
        if (conns instanceof List) {
            List<ConnectionEntry> entries = new ArrayList<>();
            for (Object o : (List<?>) conns) {
                if (o instanceof Map) {
                    entries.add(entryFromMap((Map<String, Object>) o));
                }
            }
            cfg.setConnections(entries);
        }
        Object rev = raw.get("review");
        if (rev instanceof Map) {
            cfg.setReview(reviewFromMap((Map<String, Object>) rev));
        }
        Object log = raw.get("logging");
        if (log instanceof Map) {
            cfg.setLogging(loggingFromMap((Map<String, Object>) log));
        }
        return cfg;
    }

    private static LoggingConfig loggingFromMap(Map<String, Object> m) {
        LoggingConfig l = new LoggingConfig();
        Object v = m.get("audit_log");
        if (v instanceof Boolean) l.setAuditLog((Boolean) v);
        v = m.get("mcp_console_log");
        if (v instanceof Boolean) l.setMcpConsoleLog((Boolean) v);
        String f = getStr(m, "log_file");
        if (f != null) l.setLogFile(f);
        return l;
    }

    private static ConnectionEntry entryFromMap(Map<String, Object> m) {
        ConnectionEntry e = new ConnectionEntry();
        e.setName(getStr(m, "name"));
        e.setDriver(getStr(m, "driver"));
        e.setDbType(getStr(m, "db_type"));
        e.setUrl(getStr(m, "url"));
        e.setUser(getStr(m, "user"));
        e.setPassword(getStr(m, "password"));
        e.setSchema(getStr(m, "schema"));
        e.setDatabase(getStr(m, "database"));
        return e;
    }

    private static SecurityConfig reviewFromMap(Map<String, Object> m) {
        SecurityConfig s = new SecurityConfig();
        Object kwWhole = m.get("whole_text_match");
        if (kwWhole instanceof List) {
            List<String> list = new ArrayList<>();
            for (Object o : (List<?>) kwWhole) {
                if (o != null) list.add(o.toString().trim());
            }
            s.setWholeTextMatch(list);
        }
        Object kwCmd = m.get("command_match");
        if (kwCmd instanceof List) {
            List<String> list = new ArrayList<>();
            for (Object o : (List<?>) kwCmd) {
                if (o != null) list.add(o.toString().trim());
            }
            s.setCommandMatch(list);
        }
        Object req = m.get("always_review_ddl");
        if (req instanceof Boolean) s.setAlwaysReviewDdl((Boolean) req);
        return s;
    }

    private static String getStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString().trim() : null;
    }

    static Path findConfigPath() {
        String env = System.getenv(CONFIG_ENV);
        if (env != null && !env.isBlank()) {
            Path p = Paths.get(env);
            if (Files.isRegularFile(p)) return p;
        }
        Path cwd = Paths.get("").toAbsolutePath();
        Path yaml = cwd.resolve("config.yaml");
        if (Files.isRegularFile(yaml)) return yaml;
        return null;
    }
}
