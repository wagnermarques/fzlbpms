package fzlbpms.tasktoday;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public class HttpTest {

	@Test
	public void readsRealmRoleFromKeycloakClaims() {
		Map<String, Object> claims = Map.of("realm_access",
				Map.of("roles", List.of("offline_access", "tasktoday-admin", "uma_authorization")));
		assertTrue(Http.hasRealmRole(claims, Http.ADMIN_ROLE));
	}

	@Test
	public void noRoleWhenAbsentOrMalformed() {
		assertFalse(Http.hasRealmRole(Map.of(), Http.ADMIN_ROLE));
		assertFalse(Http.hasRealmRole(Map.of("realm_access", Map.of("roles", List.of("admin"))), Http.ADMIN_ROLE));
		assertFalse(Http.hasRealmRole(Map.of("realm_access", "tasktoday-admin"), Http.ADMIN_ROLE));
		// A client role of the same name is not the realm role.
		assertFalse(Http.hasRealmRole(Map.of("resource_access",
				Map.of("fzl-tasktodayapp", Map.of("roles", List.of("tasktoday-admin")))), Http.ADMIN_ROLE));
	}
}
