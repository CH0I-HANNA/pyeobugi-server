package com.pyeobugi.server.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * 기기 비밀 토큰(deviceSecret)을 SHA-256으로 해시한다 (기기 ID 탈취 대응). DB에는 해시만 저장한다.
 * 토큰 자체가 32바이트 난수라 엔트로피가 충분해서, IpHasher처럼 서버 비밀키를 섞을 필요는 없다.
 */
@Component
public class DeviceSecretHasher {

	public String hash(String secret) {
		return HexFormat.of().formatHex(digest(secret));
	}

	/** 상수 시간 비교로 타이밍 공격을 막는다 (MessageDigest.isEqual) */
	public boolean matches(String secret, String storedHashHex) {
		return MessageDigest.isEqual(digest(secret), HexFormat.of().parseHex(storedHashHex));
	}

	private static byte[] digest(String secret) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
