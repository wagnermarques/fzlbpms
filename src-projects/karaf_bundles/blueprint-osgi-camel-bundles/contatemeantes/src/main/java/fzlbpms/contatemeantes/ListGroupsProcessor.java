package fzlbpms.contatemeantes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

public class ListGroupsProcessor implements Processor {

	private static final String SELECT_SQL = "SELECT DISTINCT group_id FROM device_status "
			+ "WHERE group_id IS NOT NULL ORDER BY group_id";

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		Message message = exchange.getMessage();
		message.removeHeaders("*");
		message.setHeader(Exchange.CONTENT_TYPE, "application/json");

		StringBuilder json = new StringBuilder("[");
		try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(SELECT_SQL);
				ResultSet rs = ps.executeQuery()) {
			boolean first = true;
			while (rs.next()) {
				if (!first) {
					json.append(",");
				}
				first = false;
				json.append("\"").append(Json.escape(rs.getString("group_id"))).append("\"");
			}
		}
		json.append("]");

		message.setBody(json.toString());
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
