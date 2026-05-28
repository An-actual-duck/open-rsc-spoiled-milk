package com.openrsc.server.database.impl.mysql;

import com.openrsc.server.Server;
import com.openrsc.server.database.DatabaseType;
import com.openrsc.server.database.JDBCDatabaseConnection;
import com.openrsc.server.util.SystemUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;

public class MySQLDatabaseConnection extends JDBCDatabaseConnection {
	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private final Server server;
	private Connection connection;
	private Statement statement;
	private boolean connected;

	public MySQLDatabaseConnection(final Server server) {
		this.server = server;
		connected = false;
	}

	public synchronized boolean open() {
		// Close the old connection before attempting to open a new connection.
		close();

		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
		} catch (final ClassNotFoundException e) {
			LOGGER.error("Could not load MySQL driver", e);
			System.exit(1);
		}

		try {
			connection = DriverManager.getConnection("jdbc:mysql://"
					+ getServer().getConfig().DB_HOST + "/" + getServer().getConfig().DB_NAME + "?autoReconnect=true&useSSL=false&rewriteBatchedStatements=true&serverTimezone=UTC",
				getServer().getConfig().DB_USER,
				getServer().getConfig().DB_PASS);
			statement = connection.createStatement();
			statement.setEscapeProcessing(true);
			connected = checkConnection();
		} catch (final SQLException e) {
			LOGGER.error("Could not connect to database", e);
			connected = false;
		}

		if(isConnected()) {
			LOGGER.info(getServer().getName() + " : " + getServer().getName() + " - Connected to MySQL!");
		} else {
			LOGGER.error("Unable to connect to MySQL");
			SystemUtil.exit(1);
		}

		return isConnected();
	}

	@Override
	public synchronized void close() {
		try {
			if(statement != null) {
				statement.close();
			}
		} catch (final SQLException e) {
			LOGGER.error("Could not close MySQL statement", e);
		}
		try {
			if(connection != null) {
				connection.close();
			}
		} catch (final SQLException e) {
			LOGGER.error("Could not close MySQL connection", e);
		}
		connected = false;
		statement = null;
		connection = null;
	}

	@Override
	public DatabaseType getDatabaseType() {
		return DatabaseType.MYSQL;
	}

	@Override
	protected boolean checkConnection() {
		try {
			getStatement().executeQuery("SELECT CURRENT_DATE");
			return true;
		} catch (final SQLException e) {
			LOGGER.warn("checkConnection() failed", e);
			return false;
		}
	}

	public final Server getServer() {
		return server;
	}

	@Override
	protected Statement getStatement() {
		return statement;
	}

	/**
	 * Get the active MySQL connection. Re-opens the existing MySQL connection if it is closed.
	 * Warning: Only use this function after the MySQL connection is already active. In other
	 * words, do not use it when we are still opening the connection like in open() and
	 * do not use it in close() either since it would try to re-open it recursively.
	 * @return Connection
	 */
	@Override
	public synchronized Connection getConnection() {
		boolean connectionNull = (connection == null);
		boolean connectionClosed = false;
		boolean connectionInvalid = false;
		boolean connectionCheckFailed = false;
		try {
			if (!connectionNull) {
				connectionClosed = connection.isClosed();
				connectionInvalid = !connection.isValid(2);
				//Technically we may not need the checkConnection() call here, and in theory it could cause lag because it's running another query entirely every single time, but it's probably fine since it's just a simple SELECT query, and we could just remove the checkConnection call at any time if we needed to.
				connectionCheckFailed = !checkConnection();
			}

			if (connectionNull) {
				LOGGER.warn("MySQL connection is null, going to reconnect...");
			}
			if (connectionClosed) {
				LOGGER.warn("MySQL connection is closed, going to reconnect...");
			}
			if (connectionInvalid) {
				LOGGER.warn("MySQL connection is invalid, going to reconnect...");
			}
			if (connectionCheckFailed) {
				LOGGER.warn("MySQL connection checkConnection() failed, going to reconnect...");
			}
			//If the connection has closed or is no longer valid (doesn't respond within 2 seconds) or a simple SELECT query fails, try re-opening the connection before trying to use it.
			if (connectionNull || connectionClosed || connectionInvalid || connectionCheckFailed) {
				open();
			}
		} catch (Exception e) {
			LOGGER.error("Connection check failed (null={}, closed={}, invalid={}, failed={}), reconnecting...", connectionNull, connectionClosed, connectionInvalid, connectionCheckFailed, e);
			open();
		}
		return connection;
	}

	public boolean isConnected() {
		return connected;
	}
}
