package fzlbpms.tasktoday;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Timer target (every 30s): pushes an alarm for each task whose
 * deadline - trigger_minutes has arrived, once per task.
 *
 * Tasks are claimed and marked alarm_fired in one UPDATE ... RETURNING over a
 * FOR UPDATE SKIP LOCKED subselect, so a task is never pushed twice even if
 * two checkers overlap. Only tasks whose owner has at least one push
 * subscription are claimed: for everyone else alarm_fired stays false and
 * the PWA's own in-app alarm (alarm-service.js) remains in charge.
 */
public class AlarmChecker {

	private static final Logger LOG = LoggerFactory.getLogger(AlarmChecker.class);
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm");

	/**
	 * Deadlines older than this are not alarmed: a task imported or created long
	 * after its deadline would otherwise ring the moment it lands.
	 */
	private static final String STALE_AFTER = "1 day";

	private static final String CLAIM_SQL = "UPDATE tasktoday_tasks t SET alarm_fired = TRUE, updated_at = now() "
			+ "WHERE t.id IN (SELECT d.id FROM tasktoday_tasks d "
			+ "  WHERE d.alarm_fired = FALSE AND d.is_archived = FALSE AND d.status <> 'CONCLUIDA' "
			+ "    AND d.alert_type <> 'none' AND d.deadline IS NOT NULL "
			+ "    AND d.deadline - make_interval(mins => COALESCE(d.trigger_minutes, 0)) <= now() "
			+ "    AND d.deadline > now() - interval '" + STALE_AFTER + "' "
			+ "    AND EXISTS (SELECT 1 FROM tasktoday_push_subscriptions s WHERE s.user_id = d.user_id) "
			+ "  ORDER BY d.deadline LIMIT 200 FOR UPDATE SKIP LOCKED) "
			+ "RETURNING t.id, t.user_id, t.title, t.deadline";

	private Db db;
	private PushService pushService;

	public void setDb(Db db) {
		this.db = db;
	}

	public void setPushService(PushService pushService) {
		this.pushService = pushService;
	}

	public void check() {
		List<Map<String, Object>> due = new ArrayList<>();
		try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(CLAIM_SQL);
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				Map<String, Object> task = new LinkedHashMap<>();
				task.put("id", rs.getString("id"));
				task.put("userId", rs.getString("user_id"));
				task.put("title", rs.getString("title"));
				task.put("deadline", rs.getTimestamp("deadline"));
				due.add(task);
			}
		} catch (SQLException e) {
			LOG.warn("Alarm check skipped, database unavailable: {}", e.getMessage());
			return;
		}

		for (Map<String, Object> task : due) {
			try {
				PushService.Result r = pushService.sendToUser((String) task.get("userId"), payloadFor(task));
				LOG.info("Alarm for task {}: sent={} failed={} removed={}", task.get("id"), r.sent, r.failed, r.removed);
			} catch (Exception e) {
				LOG.warn("Alarm for task {} not delivered: {}", task.get("id"), e.toString());
			}
		}
	}

	private static Map<String, Object> payloadFor(Map<String, Object> task) {
		Instant deadline = ((Timestamp) task.get("deadline")).toInstant();
		String when = WHEN.format(deadline.atZone(ZoneId.systemDefault()));
		boolean overdue = deadline.isBefore(Instant.now());

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("type", "task-alarm");
		data.put("taskId", task.get("id"));
		data.put("deadline", deadline.toString());

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("title", "⏰ " + task.get("title"));
		payload.put("body", (overdue ? "Prazo vencido em " : "Prazo: ") + when);
		payload.put("tag", "tasktoday-" + task.get("id"));
		payload.put("data", data);
		return payload;
	}
}
