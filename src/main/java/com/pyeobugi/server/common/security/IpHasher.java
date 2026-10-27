package com.pyeobugi.server.common.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * IP를 HMAC-SHA256(서버 비밀키)으로 해시한다 (plan 8-7).
 * IPv4는 약 43억 개뿐이라 단순 SHA-256은 전수 대입으로 원본을 되찾을 수 있으므로 비밀키를 섞는다.
 * 해시값은 Rate Limit 버킷 키로 메모리에서만 쓰고 DB와 로그에는 남기지 않는다.
 */
@Component
public class IpHasher {

	private static final String ALGORITHM = "HmacSHA256";
	private static final int MIN_SECRET_LENGTH = 32;

	private final SecretKeySpec key;

	public IpHasher(@Value("${pyeobugi.ip-hash.secret}") String secret) {
		if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
			throw new IllegalStateException("pyeobugi.ip-hash.secret은 " + MIN_SECRET_LENGTH + "자 이상이어야 합니다.");
		}
		this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	public String hash(String ip) {
		try {
			// Mac은 스레드 안전하지 않아서 호출마다 만든다
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return HexFormat.of().formatHex(mac.doFinal(ip.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("IP 해시 실패", e);
		}
	}

}
