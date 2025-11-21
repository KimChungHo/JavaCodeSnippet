package com.chungho.snippet.common;

import java.nio.charset.StandardCharsets;

public class Base64 {
	public static String encoding(String normalText) {
		byte[] arr = normalText.getBytes(StandardCharsets.UTF_8);
		String encoded = java.util.Base64.getEncoder().encodeToString(arr);

		return encoded;
	}

	public static String encoding(byte[] bytes) {
		String encoded = java.util.Base64.getEncoder().encodeToString(bytes);

		return encoded;
	}

	public static String decoding(String base64Text) {
		byte[] bytes = java.util.Base64.getDecoder().decode(base64Text);
		String decoded = new String(bytes, StandardCharsets.UTF_8);

		return decoded;
	}

	public static String decoding(byte[] bytes) {
		String decoded = new String(bytes, StandardCharsets.UTF_8);

		return decoded;
	}
}
