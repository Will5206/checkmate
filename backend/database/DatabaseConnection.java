package database;

import java.sql.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;





/**
 * this manages dathe db connections for checkmate app
 * singleton pattern to ensure only one connection pool!!
 */





public class DatabaseConnection {

    // db config stuff - checks system properties first (from Maven -D flags), then environment variables, then defaults
    // In production, environment variables should be set (Railway, Heroku, etc.)
    private static final String DB_URL = getDatabaseUrl();
    private static final String DB_USER = getDatabaseUser();
    private static final String DB_PASSWORD = getDatabasePassword();
    
    private static String getDatabaseUrl() {
        String url = System.getProperty("DB_URL");
        if (url == null || url.isEmpty()) {
            url = System.getenv("DB_URL");
        }
        // Only fall back to localhost in development
        if (url == null || url.isEmpty()) {
            String env = System.getenv("ENVIRONMENT");
            if ("production".equals(env) || "PRODUCTION".equals(env)) {
                throw new RuntimeException("DB_URL environment variable is required in production");
            }
            return "jdbc:mysql://localhost:3306/checkmate_db";
        }
        return url;
    }
    
    private static String getDatabaseUser() {
        String user = System.getProperty("DB_USER");
        if (user == null || user.isEmpty()) {
            user = System.getenv("DB_USER");
        }
        if (user == null || user.isEmpty()) {
            String env = System.getenv("ENVIRONMENT");
            if ("production".equals(env) || "PRODUCTION".equals(env)) {
                throw new RuntimeException("DB_USER environment variable is required in production");
            }
            return "root";
        }
        return user;
    }
    
    private static String getDatabasePassword() {
        String password = System.getProperty("DB_PASSWORD");
        if (password == null || password.isEmpty()) {
            password = System.getenv("DB_PASSWORD");
        }
        if (password == null || password.isEmpty()) {
            String env = System.getenv("ENVIRONMENT");
            if ("production".equals(env) || "PRODUCTION".equals(env)) {
                throw new RuntimeException("DB_PASSWORD environment variable is required in production");
            }
            return "password";
        }
        return password;
    }

    //singleton instance
    private static DatabaseConnection instance;
    private Connection connection;
    
    /**
     * private constructor for singleton pattern
     */
    private DatabaseConnection() {
        System.out.println("🔵 [DATABASE INIT] DatabaseConnection constructor called");
        System.out.println("🔵 [DATABASE INIT] DB_URL: " + DB_URL.replace(DB_PASSWORD, "***"));
        System.out.println("🔵 [DATABASE INIT] DB_USER: " + DB_USER);
        
        // Extract and display connection details
        try {
            String maskedUrl = DB_URL.replace(DB_PASSWORD, "***");
            if (maskedUrl.contains("jdbc:mysql://")) {
                String urlPart = maskedUrl.substring("jdbc:mysql://".length());
                int slashIndex = urlPart.indexOf('/');
                if (slashIndex > 0) {
                    String hostPort = urlPart.substring(0, slashIndex);
                    String database = urlPart.substring(slashIndex + 1);
                    System.out.println("🔵 [DATABASE INIT] Host:Port: " + hostPort);
                    System.out.println("🔵 [DATABASE INIT] Database: " + database);
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        try {
            System.out.println("🔵 [DATABASE INIT] Loading MySQL JDBC driver...");
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("🔵 [DATABASE INIT] JDBC driver loaded successfully");
            
            System.out.println("🔵 [DATABASE INIT] Attempting to connect to database...");
            long connStartTime = System.currentTimeMillis();
            
            // OPTIMIZED: Add connection parameters to keep connection alive and improve performance
            String optimizedUrl = DB_URL;
            if (!optimizedUrl.contains("?")) {
                optimizedUrl += "?";
            } else {
                optimizedUrl += "&";
            }
            optimizedUrl += "autoReconnect=true" +
                           "&useSSL=false" +
                           "&allowPublicKeyRetrieval=true" +
                           "&serverTimezone=UTC" +
                           "&useUnicode=true" +
                           "&characterEncoding=UTF-8" +
                           "&tcpKeepAlive=true" +
                           "&tcpNoDelay=true";
            
            this.connection = DriverManager.getConnection(optimizedUrl, DB_USER, DB_PASSWORD);
            // Set connection to not auto-close when returned from try-with-resources
            // This is safe because we manage the connection lifecycle ourselves
            long connTime = System.currentTimeMillis() - connStartTime;
            System.out.println("🔵 [DATABASE INIT] Database connection established in " + connTime + "ms");
            
            // Verify connection and get metadata
            if (this.connection != null && !this.connection.isClosed()) {
                try {
                    String catalog = this.connection.getCatalog();
                    String url = this.connection.getMetaData().getURL();
                    String dbProduct = this.connection.getMetaData().getDatabaseProductName();
                    String dbVersion = this.connection.getMetaData().getDatabaseProductVersion();
                    System.out.println("🔵 [DATABASE INIT] Connection verified:");
                    System.out.println("🔵 [DATABASE INIT]   - Catalog (Database): " + catalog);
                    System.out.println("🔵 [DATABASE INIT]   - Product: " + dbProduct);
                    System.out.println("🔵 [DATABASE INIT]   - Version: " + dbVersion);
                    System.out.println("🔵 [DATABASE INIT]   - Connection URL: " + url.replace(DB_PASSWORD, "***"));
                } catch (SQLException e) {
                    System.out.println("🔵 [DATABASE INIT] Connection established but metadata unavailable");
                }
            }
            System.out.println("🔵 [DATABASE INIT] Database connection established successfully");
            
        } catch (ClassNotFoundException e) {
            System.err.println("🔴 [DATABASE INIT ERROR] MySQL JDBC Driver not found");
            System.err.println("🔴 [DATABASE INIT ERROR] Message: " + e.getMessage());
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("🔴 [DATABASE INIT ERROR] Failed to connect to database");
            System.err.println("🔴 [DATABASE INIT ERROR] Message: " + e.getMessage());
            System.err.println("🔴 [DATABASE INIT ERROR] SQL State: " + e.getSQLState());
            System.err.println("🔴 [DATABASE INIT ERROR] Error Code: " + e.getErrorCode());
            e.printStackTrace();
        }
    }
    
    /**
     * get singleton instance of DatabaseConnection
     * @return DatabaseConnection instance
     */
    public static DatabaseConnection getInstance() {
        if (instance == null) {
            synchronized (DatabaseConnection.class) {
                if (instance == null) {
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }
    
    /**
     * get the active database connection
     * This is now thread-safe ---- ensures only one connection is created at a time
     * @return Connection object (never null)
     * @throws SQLException if connection cannot be established
     */
    /**
     * OPTIMIZED: Get connection with improved connection management.
     * Reduces connection overhead by:
     * 1. Less aggressive validation (only when connection appears closed)
     * 2. Connection URL parameters to keep connections alive
     * 3. Reduced logging overhead
     * 
     * @return Connection object (wrapped to prevent accidental closure)
     * @throws SQLException if connection cannot be established
     */
    public synchronized Connection getConnection() throws SQLException {
        // OPTIMIZED: Reduced logging - only log when reconnecting
        boolean needsReconnect = false;
        
        if (connection == null) {
            needsReconnect = true;
        } else {
            try {
                // Only check if closed, don't validate every time (isValid is expensive)
                if (connection.isClosed()) {
                    needsReconnect = true;
                }
                // If not closed, assume it's valid and reuse (MySQL autoReconnect will handle issues)
            } catch (SQLException e) {
                // Connection check failed, need to reconnect
                needsReconnect = true;
            }
        }
        
        if (needsReconnect) {
            System.out.println("🔵 [DATABASE] Creating new connection (took " + 
                             (connection == null ? "N/A" : "closed") + ")...");
            
            long connStartTime = System.currentTimeMillis();
            try {
                // Use optimized URL with connection parameters
                String optimizedUrl = DB_URL;
                if (!optimizedUrl.contains("?")) {
                    optimizedUrl += "?";
                } else {
                    optimizedUrl += "&";
                }
                optimizedUrl += "autoReconnect=true" +
                               "&useSSL=false" +
                               "&allowPublicKeyRetrieval=true" +
                               "&serverTimezone=UTC" +
                               "&useUnicode=true" +
                               "&characterEncoding=UTF-8" +
                               "&tcpKeepAlive=true" +
                               "&tcpNoDelay=true";
                
                connection = DriverManager.getConnection(optimizedUrl, DB_USER, DB_PASSWORD);
                long connTime = System.currentTimeMillis() - connStartTime;
                System.out.println("🔵 [DATABASE] Connection established in " + connTime + "ms");
            } catch (SQLException e) {
                System.err.println("🔴 [DATABASE ERROR] Failed to create connection: " + e.getMessage());
                throw e;
            }
        }
        
        // Return a wrapper that prevents accidental closure
        return new ConnectionWrapper(connection);
    }
    
    /**
     * Wrapper class to prevent accidental closure of the singleton connection.
     * This allows try-with-resources to work without actually closing the connection.
     */
    private static class ConnectionWrapper implements Connection {
        private final Connection delegate;
        
        public ConnectionWrapper(Connection delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public void close() throws SQLException {
            // Don't actually close - just reset auto-commit and clear any warnings
            // This allows try-with-resources to work without breaking the singleton
            try {
                if (delegate != null && !delegate.isClosed()) {
                    delegate.setAutoCommit(true); // Reset to default
                    delegate.clearWarnings();
                }
            } catch (SQLException e) {
                // If connection is already closed, that's fine
            }
        }
        
        // Delegate all other Connection methods to the actual connection
        @Override
        public Statement createStatement() throws SQLException {
            return delegate.createStatement();
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            return delegate.prepareStatement(sql);
        }
        
        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            return delegate.prepareCall(sql);
        }
        
        @Override
        public String nativeSQL(String sql) throws SQLException {
            return delegate.nativeSQL(sql);
        }
        
        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            delegate.setAutoCommit(autoCommit);
        }
        
        @Override
        public boolean getAutoCommit() throws SQLException {
            return delegate.getAutoCommit();
        }
        
        @Override
        public void commit() throws SQLException {
            delegate.commit();
        }
        
        @Override
        public void rollback() throws SQLException {
            delegate.rollback();
        }
        
        @Override
        public boolean isClosed() throws SQLException {
            return delegate.isClosed();
        }
        
        @Override
        public DatabaseMetaData getMetaData() throws SQLException {
            return delegate.getMetaData();
        }
        
        @Override
        public void setReadOnly(boolean readOnly) throws SQLException {
            delegate.setReadOnly(readOnly);
        }
        
        @Override
        public boolean isReadOnly() throws SQLException {
            return delegate.isReadOnly();
        }
        
        @Override
        public void setCatalog(String catalog) throws SQLException {
            delegate.setCatalog(catalog);
        }
        
        @Override
        public String getCatalog() throws SQLException {
            return delegate.getCatalog();
        }
        
        @Override
        public void setTransactionIsolation(int level) throws SQLException {
            delegate.setTransactionIsolation(level);
        }
        
        @Override
        public int getTransactionIsolation() throws SQLException {
            return delegate.getTransactionIsolation();
        }
        
        @Override
        public SQLWarning getWarnings() throws SQLException {
            return delegate.getWarnings();
        }
        
        @Override
        public void clearWarnings() throws SQLException {
            delegate.clearWarnings();
        }
        
        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            return delegate.createStatement(resultSetType, resultSetConcurrency);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency);
        }
        
        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            return delegate.prepareCall(sql, resultSetType, resultSetConcurrency);
        }
        
        @Override
        public java.util.Map<String, Class<?>> getTypeMap() throws SQLException {
            return delegate.getTypeMap();
        }
        
        @Override
        public void setTypeMap(java.util.Map<String, Class<?>> map) throws SQLException {
            delegate.setTypeMap(map);
        }
        
        @Override
        public void setHoldability(int holdability) throws SQLException {
            delegate.setHoldability(holdability);
        }
        
        @Override
        public int getHoldability() throws SQLException {
            return delegate.getHoldability();
        }
        
        @Override
        public Savepoint setSavepoint() throws SQLException {
            return delegate.setSavepoint();
        }
        
        @Override
        public Savepoint setSavepoint(String name) throws SQLException {
            return delegate.setSavepoint(name);
        }
        
        @Override
        public void rollback(Savepoint savepoint) throws SQLException {
            delegate.rollback(savepoint);
        }
        
        @Override
        public void releaseSavepoint(Savepoint savepoint) throws SQLException {
            delegate.releaseSavepoint(savepoint);
        }
        
        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        
        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            return delegate.prepareStatement(sql, autoGeneratedKeys);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            return delegate.prepareStatement(sql, columnIndexes);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            return delegate.prepareStatement(sql, columnNames);
        }
        
        @Override
        public Clob createClob() throws SQLException {
            return delegate.createClob();
        }
        
        @Override
        public Blob createBlob() throws SQLException {
            return delegate.createBlob();
        }
        
        @Override
        public NClob createNClob() throws SQLException {
            return delegate.createNClob();
        }
        
        @Override
        public SQLXML createSQLXML() throws SQLException {
            return delegate.createSQLXML();
        }
        
        @Override
        public boolean isValid(int timeout) throws SQLException {
            return delegate.isValid(timeout);
        }
        
        @Override
        public void setClientInfo(String name, String value) throws SQLClientInfoException {
            delegate.setClientInfo(name, value);
        }
        
        @Override
        public void setClientInfo(java.util.Properties properties) throws SQLClientInfoException {
            delegate.setClientInfo(properties);
        }
        
        @Override
        public String getClientInfo(String name) throws SQLException {
            return delegate.getClientInfo(name);
        }
        
        @Override
        public java.util.Properties getClientInfo() throws SQLException {
            return delegate.getClientInfo();
        }
        
        @Override
        public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
            return delegate.createArrayOf(typeName, elements);
        }
        
        @Override
        public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
            return delegate.createStruct(typeName, attributes);
        }
        
        @Override
        public void setSchema(String schema) throws SQLException {
            delegate.setSchema(schema);
        }
        
        @Override
        public String getSchema() throws SQLException {
            return delegate.getSchema();
        }
        
        @Override
        public void abort(java.util.concurrent.Executor executor) throws SQLException {
            delegate.abort(executor);
        }
        
        @Override
        public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) throws SQLException {
            delegate.setNetworkTimeout(executor, milliseconds);
        }
        
        @Override
        public int getNetworkTimeout() throws SQLException {
            return delegate.getNetworkTimeout();
        }
        
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }
        
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }
    
    /**
     * init db schema from sql file
     * @param schemaFilePath path to schema.sql file
     * @return true if successful, false otherwise
     */
    public boolean initializeSchema(String schemaFilePath) {
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            

            BufferedReader reader = new BufferedReader(new FileReader(schemaFilePath));
            StringBuilder sql = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }
                sql.append(line).append(" ");
            }
            reader.close();
            
            //split by semicolon and execute each statement
            String[] statements = sql.toString().split(";");
            for (String statement : statements) {
                if (!statement.trim().isEmpty()) {
                    stmt.execute(statement);
                }
            }
            


            System.out.println("Database schema initialized successfully");
            stmt.close();
            return true;
            
        } catch (SQLException e) {
            System.err.println("SQL error initializing schema");
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            System.err.println("Error reading schema file");
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Test database connection.
     * @return true if connection is valid, false otherwise
     */
    public boolean testConnection() {
        try {
            Connection conn = getConnection();
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }




    /**
     * Close database connection.
     * Thread-safe method to safely close the shared connection.
     */
    public synchronized void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
            e.printStackTrace();
        }
    }
}