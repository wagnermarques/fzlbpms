package fzlbpms.bpmndrawer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Db {

	private static final Logger LOG = LoggerFactory.getLogger(Db.class);

	private final DataSource dataSource;
	private volatile boolean schemaReady;

	public Db() {
		PGSimpleDataSource ds = new PGSimpleDataSource();
		ds.setServerNames(new String[] { Env.get("BPMNDRAWER_DB_HOST", Env.get("FZL_POSTGRES_HOST", "fzl-postgresql")) });
		ds.setPortNumbers(new int[] { Integer.parseInt(Env.get("BPMNDRAWER_DB_PORT", Env.get("FZL_POSTGRES_PORT", "5432"))) });
		ds.setDatabaseName(Env.get("BPMNDRAWER_DB_NAME", Env.get("FZL_POSTGRES_DB", "fzldb")));
		ds.setUser(Env.get("BPMNDRAWER_DB_USER", Env.get("FZL_POSTGRES_USER", "postgres")));
		ds.setPassword(Env.get("BPMNDRAWER_DB_PASS", Env.get("FZL_POSTGRES_PASSWORD", "1234")));
		this.dataSource = ds;
	}

	public void init() {
		try (Connection conn = getConnection()) {
			LOG.info("BPMN Drawer schema verified successfully in database");
		} catch (Exception e) {
			LOG.warn("BPMN Drawer schema not applied yet (will retry on first request): {}", e.getMessage());
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
			st.execute(readSchema());
		}
		schemaReady = true;
		LOG.info("BPMN Drawer schema initialized from /sql/schema.sql");
	}

	private static String readSchema() {
		try (InputStream in = Db.class.getResourceAsStream("/sql/schema.sql")) {
			if (in == null) throw new IllegalStateException("sql/schema.sql missing from bpmndrawer bundle");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Cannot read sql/schema.sql", e);
		}
	}
}
