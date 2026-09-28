package fzlbpms.chamadas;

import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;

public final class ChamadasDataSourceFactory {

	private ChamadasDataSourceFactory() {
	}

	public static DataSource create() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setServerNames(new String[] { env("CHAMADAS_DB_HOST", "fzl-postgresql") });
		dataSource.setPortNumbers(new int[] { Integer.parseInt(env("CHAMADAS_DB_PORT", "5432")) });
		dataSource.setDatabaseName(env("CHAMADAS_DB_NAME", env("FZL_POSTGRES_DB", "fzldb")));
		dataSource.setUser(env("CHAMADAS_DB_USER", env("FZL_POSTGRES_USER", "postgres")));
		dataSource.setPassword(env("CHAMADAS_DB_PASS", env("FZL_POSTGRES_PASSWORD", "1234")));
		return dataSource;
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return (value == null || value.isBlank()) ? fallback : value;
	}
}
