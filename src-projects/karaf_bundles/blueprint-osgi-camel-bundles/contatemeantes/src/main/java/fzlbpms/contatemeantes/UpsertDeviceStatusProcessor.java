package fzlbpms.contatemeantes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

public class UpsertDeviceStatusProcessor implements Processor {

	// group_id is COALESCEd rather than fully overwritten like the other
	// fields: it's group *membership*, set once at pairing time, not
	// per-ping telemetry — a POST that omits it (the common case) must not
	// silently drop the device out of its group.
	private static final String UPSERT_SQL = "INSERT INTO device_status "
			+ "(device_id, user_name, latitude, longitude, accuracy, battery_level, is_charging, last_seen, group_id) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?, now(), ?) " + "ON CONFLICT (device_id) DO UPDATE SET "
			+ "user_name = EXCLUDED.user_name, latitude = EXCLUDED.latitude, longitude = EXCLUDED.longitude, "
			+ "accuracy = EXCLUDED.accuracy, battery_level = EXCLUDED.battery_level, "
			+ "is_charging = EXCLUDED.is_charging, last_seen = now(), "
			+ "group_id = COALESCE(EXCLUDED.group_id, device_status.group_id)";

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		String deviceId = requiredString(exchange, "deviceId");
		double latitude = requiredDouble(exchange, "latitude");
		double longitude = requiredDouble(exchange, "longitude");
		String userName = optionalString(exchange, "userName");
		Double accuracy = optionalDouble(exchange, "accuracy");
		Integer batteryLevel = optionalInt(exchange, "batteryLevel");
		Boolean isCharging = optionalBoolean(exchange, "isCharging");
		String groupId = optionalString(exchange, "groupId");

		try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
			ps.setString(1, deviceId);
			ps.setString(2, userName);
			ps.setDouble(3, latitude);
			ps.setDouble(4, longitude);
			setNullableDouble(ps, 5, accuracy);
			setNullableInt(ps, 6, batteryLevel);
			setNullableBoolean(ps, 7, isCharging);
			ps.setString(8, groupId);
			ps.executeUpdate();
		}

		Message message = exchange.getMessage();
		message.setBody("{\"status\":\"ok\",\"deviceId\":\"" + Json.escape(deviceId) + "\"}");
		message.setHeader(Exchange.CONTENT_TYPE, "application/json");
	}

	private void setNullableDouble(PreparedStatement ps, int index, Double value) throws java.sql.SQLException {
		if (value == null) {
			ps.setNull(index, Types.DOUBLE);
		} else {
			ps.setDouble(index, value);
		}
	}

	private void setNullableInt(PreparedStatement ps, int index, Integer value) throws java.sql.SQLException {
		if (value == null) {
			ps.setNull(index, Types.INTEGER);
		} else {
			ps.setInt(index, value);
		}
	}

	private void setNullableBoolean(PreparedStatement ps, int index, Boolean value) throws java.sql.SQLException {
		if (value == null) {
			ps.setNull(index, Types.BOOLEAN);
		} else {
			ps.setBoolean(index, value);
		}
	}

	private String requiredString(Exchange exchange, String propertyName) {
		String value = optionalString(exchange, propertyName);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing required field: " + propertyName + ".");
		}
		return value;
	}

	private String optionalString(Exchange exchange, String propertyName) {
		Object value = exchange.getProperty(propertyName);
		return value == null ? null : value.toString();
	}

	private double requiredDouble(Exchange exchange, String propertyName) {
		Double value = optionalDouble(exchange, propertyName);
		if (value == null) {
			throw new IllegalArgumentException("Missing required field: " + propertyName + ".");
		}
		return value;
	}

	private Double optionalDouble(Exchange exchange, String propertyName) {
		String value = optionalString(exchange, propertyName);
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Double.valueOf(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Field " + propertyName + " must be a number.");
		}
	}

	private Integer optionalInt(Exchange exchange, String propertyName) {
		String value = optionalString(exchange, propertyName);
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Field " + propertyName + " must be an integer.");
		}
	}

	private Boolean optionalBoolean(Exchange exchange, String propertyName) {
		String value = optionalString(exchange, propertyName);
		return value == null || value.isBlank() ? null : Boolean.valueOf(value);
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
