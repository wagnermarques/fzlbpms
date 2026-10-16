package fzlbpms.orbeonforms;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Access to Orbeon's own PostgreSQL database — the schema belongs to Orbeon, so
 * this bundle never creates or migrates tables, it only reads the published form
 * catalogue and writes form definitions the same way publish_forms.py does.
 */
public class OrbeonDb {

	private final DataSource dataSource;

	public OrbeonDb() {
		PGSimpleDataSource ds = new PGSimpleDataSource();
		ds.setServerNames(new String[] { Env.get("ORBEON_DB_HOST", Env.get("FZL_POSTGRES_HOST", "fzl-postgresql")) });
		ds.setPortNumbers(new int[] { Integer.parseInt(Env.get("ORBEON_DB_PORT", Env.get("FZL_POSTGRES_PORT", "5432"))) });
		ds.setDatabaseName(Env.get("FZL_ORBEON_DB_NAME", "orbeon"));
		ds.setUser(Env.get("ORBEON_DB_USER", Env.get("FZL_POSTGRES_USER", "postgres")));
		ds.setPassword(Env.get("ORBEON_DB_PASS", Env.get("FZL_POSTGRES_PASSWORD", "1234")));
		this.dataSource = ds;
	}

	public Connection getConnection() throws SQLException {
		return dataSource.getConnection();
	}
}
