package fzlbpms.tasktoday;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connection source for the Task Today tables in fzl-postgresql (fzldb).
 *
 * The schema is applied on bundle start (blueprint init-method) and, when
 * PostgreSQL was not reachable yet — Karaf often boots before the database —
 * again on the first connection handed out, so the bundle never fails to
 * start just because it won the race.
 */
public class Db {

	private static final Logger LOG = LoggerFactory.getLogger(Db.class);

	private final DataSource dataSource;
	private volatile boolean schemaReady;

	public Db() {
		PGSimpleDataSource ds = new PGSimpleDataSource();
		ds.setServerNames(new String[] { Env.get("TASKTODAY_DB_HOST", "fzl-postgresql") });
		ds.setPortNumbers(new int[] { Integer.parseInt(Env.get("TASKTODAY_DB_PORT", "5432")) });
		ds.setDatabaseName(Env.get("TASKTODAY_DB_NAME", Env.get("FZL_POSTGRES_DB", "fzldb")));
		ds.setUser(Env.get("TASKTODAY_DB_USER", Env.get("FZL_POSTGRES_USER", "postgres")));
		ds.setPassword(Env.get("TASKTODAY_DB_PASS", Env.get("FZL_POSTGRES_PASSWORD", "1234")));
		this.dataSource = ds;
	}

	/** Blueprint init-method: best effort, never throws. */
	public void init() {
		try (Connection conn = getConnection()) {
			LOG.info("Task Today schema ready");
		} catch (Exception e) {
			LOG.warn("Task Today schema not applied yet (will retry on first use): {}", e.getMessage());
		}
	}

	public Connection getConnection() throws SQLException {
		Connection conn = dataSource.getConnection();
		if (!schemaReady) {
			try {
				applySchema(conn);
			} catch (SQLException e) {
				conn.close();
				throw e;
			}
		}
		return conn;
	}

	private synchronized void applySchema(Connection conn) throws SQLException {
		if (schemaReady) return;
		try (Statement st = conn.createStatement()) {
			// Simple-query protocol: the driver sends the whole script at once.
			st.execute(readSchema());
		}
		schemaReady = true;
	}

	private static String readSchema() {
		try (InputStream in = Db.class.getResourceAsStream("/sql/schema.sql")) {
			if (in == null) throw new IllegalStateException("sql/schema.sql missing from bundle");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Cannot read sql/schema.sql", e);
		}
	}
}
