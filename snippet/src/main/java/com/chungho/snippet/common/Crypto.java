package com.chungho.snippet.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class Crypto {

	private final ObjectMapper objectMapper;
	private String rsaPubKey = "";   // "-----BEGIN PUBLIC KEY----- ... -----END PUBLIC KEY-----"
	@Getter
	private byte[] aesKey;
	@Getter
	private byte[] aesIv;
	@Setter
	@Getter
	private HttpServletRequest httpRequest;

	public Crypto() {
		this.objectMapper = createObjectMapper();
	}

	public Crypto(byte[] aesKey, byte[] aesIv) {
		this();
		this.aesKey = aesKey;
		this.aesIv = aesIv;
	}

	private ObjectMapper createObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();

		// enum을 문자열로 (C#의 JsonStringEnumConverter와 비슷한 설정)
		mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
		mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);

		return mapper;
	}

	/*
	 * 클라이언트 기준 코드.
	 */
	public String encryptWithRsa(String request) throws Exception {
		String rsaTrim = rsaPubKey
				.replace("-----BEGIN PUBLIC KEY-----", "")
				.replace("-----END PUBLIC KEY-----", "")
				.replace("\r", "")
				.replace("\n", "")
				.trim();

		// AES 키/IV 생성
		KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
		keyGenerator.init(256); // 환경에 따라 128도 가능
		SecretKey secretKey = keyGenerator.generateKey();
		byte[] keyBytes = secretKey.getEncoded();

		byte[] ivBytes = new byte[16];
		SecureRandom secureRandom = new SecureRandom();
		secureRandom.nextBytes(ivBytes);

		this.aesKey = keyBytes;
		this.aesIv = ivBytes;

		// 원문을 AES로 암호화 (Base64 문자열)
		String aesString = AES.encryptWithBase64(request, keyBytes, ivBytes);

		AesPayload aesPayload = new AesPayload();
		aesPayload.aesKey = keyBytes;
		// C# 코드에는 aesPayload.IV = aes.Key 로 되어 있지만, 논리적으로는 ivBytes가 맞습니다.
		// C# 원본과 완전히 동일하게 가려면 아래 한 줄을 keyBytes로 바꿔도 됩니다.
		aesPayload.iv = ivBytes;
		aesPayload.payload = aesString;

		String aesJson = objectMapper.writeValueAsString(aesPayload);
		byte[] aesBytes = aesJson.getBytes(StandardCharsets.UTF_8);

		// RSA 공개키 로드
		byte[] pubKeyBytes = java.util.Base64.getDecoder().decode(rsaTrim);
		X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(pubKeyBytes);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PublicKey publicKey = keyFactory.generatePublic(pubKeySpec);

		Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
		rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);

		byte[] rsaEncKey = rsaCipher.doFinal(keyBytes);
		byte[] rsaEncIv = rsaCipher.doFinal(ivBytes);

		RsaPayload rsaPayload = new RsaPayload();
		rsaPayload.aesKey = rsaEncKey;
		rsaPayload.iv = rsaEncIv;
		rsaPayload.payload = aesBytes;

		String rsaJson = objectMapper.writeValueAsString(rsaPayload);

		// 최종 Base64 인코딩
		String body = Base64.encoding(rsaJson);

		return body;
	}

	/*
	 * 클라이언트 응답용 암호화 코드.
	 */
	public String encrypt(String request) throws Exception {
		String requestJson = objectMapper.writeValueAsString(request);

		String aesString = AES.encryptWithBase64(requestJson, aesKey, aesIv);

		AesPayload aesPayload = new AesPayload();
		aesPayload.payload = aesString;

		String aesJson = objectMapper.writeValueAsString(aesPayload);

		String body = Base64.encoding(aesJson);

		return body;
	}

	/*
	 * 클라이언트로부터 받은 데이터 복호화하는 코드.
	 * (서버 기준, RSA 개인키 사용)
	 */
	public <T> T decrypt(String encryptedString, Class<T> myClass) throws Exception {
		String rsaTrim = RSA.privateKey
				.replace("-----BEGIN RSA PRIVATE KEY-----", "")
				.replace("-----END RSA PRIVATE KEY-----", "")
				.replace("\r", "")
				.replace("\n", "")
				.trim();

		String base64DecodedString = Base64.decoding(encryptedString);

		RsaPayload rsaPayload = objectMapper.readValue(base64DecodedString, RsaPayload.class);

		// RSA 개인키 로드 (주의: 여기서는 PKCS#8 포맷("BEGIN PRIVATE KEY")을 기대함)
		byte[] privateKeyBytes = java.util.Base64.getDecoder().decode(rsaTrim);
		PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

		Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
		rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);

		byte[] aesKeyBytes = rsaCipher.doFinal(rsaPayload.aesKey);
		byte[] aesIvBytes = rsaCipher.doFinal(rsaPayload.iv);

		String aesString = new String(rsaPayload.payload, StandardCharsets.UTF_8);
		AesPayload aesPayload = objectMapper.readValue(aesString, AesPayload.class);

		String aesDecryptString = AES.decryptWithBase64(
				aesPayload.payload,
				aesKeyBytes,
				aesIvBytes
		);

		T result = objectMapper.readValue(aesDecryptString, myClass);

		return result;
	}

	/*
	 * 클라이언트 기준 코드.
	 * RSA를 사용하지 않고, 이미 공유된 AES Key/IV로만 복호화.
	 */
	public <T> T decryptWithoutRsa(String encryptedString, Class<T> myClass) throws Exception {
		String base64DecodedString = Base64.decoding(encryptedString);

		AesPayload aesPayload = objectMapper.readValue(base64DecodedString, AesPayload.class);

		String aesDecryptString = AES.decryptWithBase64(aesPayload.payload, aesKey, aesIv);

		// C#의 JsonSerializer.Deserialize<string>(aesDecryptString)에 해당
		String deserializeString = objectMapper.readValue(aesDecryptString, String.class);

		T result = objectMapper.readValue(deserializeString, myClass);

		return result;
	}

	public static class RsaPayload {
		public byte[] aesKey;
		public byte[] iv;
		public byte[] payload;
	}

	public static class AesPayload {
		public byte[] aesKey;
		public byte[] iv;
		public String payload;
	}

	/*
	 * privateKey는 다른 곳에서 한 번 로드해 두고 사용하면 됨.
	 */
	public static class RSA {
		public static String privateKey;

		public RSA() {
			// TODO: AWS SSM 등에서 Key를 읽어와 privateKey에 넣어주면 됨.
		}
	}

	/*
	 * AES/CBC/PKCS5Padding + Base64 인코딩/디코딩.
	 */
	public static class AES {

		public static String encryptWithBase64(String plainText, byte[] key, byte[] iv) throws Exception {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

			byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

			return Base64.decoding(cipherBytes);
		}

		public static String decryptWithBase64(String cipherTextBase64, byte[] key, byte[] iv) throws Exception {
			byte[] cipherBytes = java.util.Base64.getDecoder().decode(cipherTextBase64);

			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

			byte[] plainBytes = cipher.doFinal(cipherBytes);

			return new String(plainBytes, StandardCharsets.UTF_8);
		}
	}
}