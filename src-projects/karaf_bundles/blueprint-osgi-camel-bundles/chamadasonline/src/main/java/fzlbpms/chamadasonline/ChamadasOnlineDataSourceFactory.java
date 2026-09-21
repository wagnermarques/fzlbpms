package fzlbpms.chamadasonline;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;

public final class ChamadasOnlineDataSourceFactory {

	private ChamadasOnlineDataSourceFactory() {
	}

	public static DataSource create() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setServerNames(new String[] { env("CHAMADASONLINE_DB_HOST", "fzl-postgresql") });
		dataSource.setPortNumbers(new int[] { Integer.parseInt(env("CHAMADASONLINE_DB_PORT", "5432")) });
		dataSource.setDatabaseName(env("CHAMADASONLINE_DB_NAME", "chamadasonline"));
		dataSource.setUser(env("CHAMADASONLINE_DB_USER", "chamadasonline"));
		dataSource.setPassword(System.getenv("CHAMADASONLINE_DB_PASS"));
		return dataSource;
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return (value == null || value.isBlank()) ? fallback : value;
	}
}
