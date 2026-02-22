package com.alvinliu.dbmcp.config;

/**
 * One database connection (matches config.yaml connections[]).
 * JDBC: driver + url; user/password optional.
 * db_type: Druid DbType name (mysql, oracle, postgresql, sql_server, etc.); omit for default.
 */
public class ConnectionEntry {
    private String name;
    private String driver;
    private String dbType;   // Alibaba Druid DbType (e.g. mysql, oracle, postgresql, sql_server)
    private String url;     // JDBC URL
    private String user;
    private String password;
    private String schema;
    private String database;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDriver() { return driver; }
    public void setDriver(String driver) { this.driver = driver; }

    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
}
