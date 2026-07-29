package com.openrsc.layeredmaps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class Hashes {
	private Hashes() {
	}

	static String sha256(Path path) throws IOException {
		MessageDigest digest = digest();
		byte[] buffer = new byte[64 * 1024];
		try (InputStream input = Files.newInputStream(path)) {
			int count;
			while ((count = input.read(buffer)) != -1) {
				digest.update(buffer, 0, count);
			}
		}
		return hex(digest.digest());
	}

	static String sha256(String value) {
		MessageDigest digest = digest();
		digest.update(value.getBytes(StandardCharsets.UTF_8));
		return hex(digest.digest());
	}

	static String sha256(byte[] value) {
		MessageDigest digest = digest();
		digest.update(value);
		return hex(digest.digest());
	}

	private static MessageDigest digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is unavailable", failure);
		}
	}

	private static String hex(byte[] value) {
		StringBuilder result = new StringBuilder(value.length * 2);
		for (byte part : value) {
			result.append(String.format("%02x", part & 0xff));
		}
		return result.toString();
	}
}
