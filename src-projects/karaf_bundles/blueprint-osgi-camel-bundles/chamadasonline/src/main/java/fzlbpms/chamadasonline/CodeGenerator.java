package fzlbpms.chamadasonline;

import java.security.SecureRandom;

/** Direct port of apps/api/src/lib/codeGenerator.ts. */
public final class CodeGenerator {

	// No 0/O/1/I, to avoid projector/typing misreads.
	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	private CodeGenerator() {
	}

	public static String generate(int length) {
		StringBuilder code = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
		}
		return code.toString();
	}
}
