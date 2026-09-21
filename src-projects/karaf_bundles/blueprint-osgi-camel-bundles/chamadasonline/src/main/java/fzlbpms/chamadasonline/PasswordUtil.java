package fzlbpms.chamadasonline;

import org.mindrot.jbcrypt.BCrypt;

/** Ports the bcryptjs calls in apps/api/src/routes/auth.ts and seed.ts. */
public final class PasswordUtil {

	private PasswordUtil() {
	}

	public static String hash(String pin) {
		return BCrypt.hashpw(pin, BCrypt.gensalt());
	}

	/** True if pin matches hash. False (not an exception) for a blank hash — mirrors
	 *  Keycloak-provisioned users, which are created with pinHash: "" in auth.ts since
	 *  their authentication is delegated to Keycloak entirely. */
	public static boolean matches(String pin, String hash) {
		if (hash == null || hash.isBlank()) return false;
		return BCrypt.checkpw(pin, hash);
	}
}
