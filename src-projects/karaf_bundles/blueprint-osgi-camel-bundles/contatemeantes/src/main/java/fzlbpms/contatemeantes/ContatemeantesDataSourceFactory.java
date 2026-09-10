package fzlbpms.contatemeantes;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;

public final class ContatemeantesDataSourceFactory {

	private ContatemeantesDataSourceFactory() {
	}

	public static DataSource create() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setServerNames(new String[] { env("CONTATEMEANTES_DB_HOST", "fzl-postgresql") });
		dataSource.setPortNumbers(new int[] { Integer.parseInt(env("CONTATEMEANTES_DB_PORT", "5432")) });
		dataSource.setDatabaseName(env("CONTATEMEANTES_DB_NAME", "contatemeantes"));
		dataSource.setUser(env("CONTATEMEANTES_DB_USER", "contatemeantes"));
		dataSource.setPassword(System.getenv("CONTATEMEANTES_DB_PASS"));
		return dataSource;
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return (value == null || value.isBlank()) ? fallback : value;
	}
}
