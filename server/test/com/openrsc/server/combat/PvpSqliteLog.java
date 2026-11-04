package com.openrsc.server.combat;

import com.openrsc.server.Server;
import com.openrsc.server.database.DatabaseType;
import com.openrsc.server.database.JDBCDatabaseConnection;
import com.openrsc.server.database.impl.mysql.MySqlGameDatabase;
import com.openrsc.server.database.impl.mysql.MySqlGameLogger;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Real production PK logger against an isolated in-memory SQLite database. */
final class PvpSqliteLog implements AutoCloseable {
    private final Server server;
    private final Connection jdbc;
    private final MySqlGameLogger logger;
    private final Object previousDatabase, previousLogger;
    private final String table;
    PvpSqliteLog(Server server) throws Exception {
        this.server=server;
        jdbc=DriverManager.getConnection("jdbc:sqlite::memory:");
        table=server.getConfig().DB_TABLE_PREFIX+"live_feeds";
        if(!table.matches("[A-Za-z0-9_]+"))throw new AssertionError("Unsafe fixture table");
        jdbc.createStatement().execute("CREATE TABLE "+table+" (username TEXT,message TEXT,time INTEGER)");
        jdbc.createStatement().execute("CREATE TABLE "+server.getConfig().DB_TABLE_PREFIX+"generic_logs (message TEXT,time INTEGER)");
        final JDBCDatabaseConnection connection=new JDBCDatabaseConnection() {
            protected Statement getStatement() {try{return jdbc.createStatement();}catch(SQLException e){throw new IllegalStateException(e);}}
            public Connection getConnection(){return jdbc;}
            protected boolean checkConnection(){return true;}
            public boolean isConnected(){return true;}
            public boolean open(){return true;}
            public void close(){}
            public DatabaseType getDatabaseType(){return DatabaseType.SQLITE;}
        };
        MySqlGameDatabase database=new MySqlGameDatabase(server) {
            @Override public JDBCDatabaseConnection getConnection(){return connection;}
        };
        previousDatabase=field("database").get(server);previousLogger=field("gameLogger").get(server);
        logger=new MySqlGameLogger(server,database);
        field("database").set(server,database);field("gameLogger").set(server,logger);
        server.getConfig().WANT_DISCORD_BOT=false;
        logger.start();
    }
    private static Field field(String name)throws Exception {Field f=Server.class.getDeclaredField(name);f.setAccessible(true);return f;}
    int count()throws Exception {
        logger.run();
        try(Statement s=jdbc.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)) {r.next();return r.getInt(1);}
    }
    int deathCount() throws Exception {
        logger.run();
        try (Statement s=jdbc.createStatement(); ResultSet r=s.executeQuery(
                "SELECT COUNT(*) FROM "+server.getConfig().DB_TABLE_PREFIX+"generic_logs")) {
            r.next(); return r.getInt(1);
        }
    }
    @Override public void close()throws Exception {
        logger.run();logger.stop();jdbc.close();
        field("database").set(server,previousDatabase);field("gameLogger").set(server,previousLogger);
    }
}
