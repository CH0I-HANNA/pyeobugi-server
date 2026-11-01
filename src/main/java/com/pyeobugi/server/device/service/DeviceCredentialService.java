package com.pyeobugi.server.device.service;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pyeobugi.server.common.error.ApiException;
import com.pyeobugi.server.common.error.ErrorCode;
import com.pyeobugi.server.common.security.DeviceSecretHasher;
import com.pyeobugi.server.common.time.KstClock;
import com.pyeobugi.server.device.repository.DeviceCredentialRepository;

import lombok.RequiredArgsConstructor;

/** 기기별 비밀 토큰 발급·검증. X-Device-Id만으로는 다른 기기를 흉내 낼 수 있던 문제(탈취)를 보완한다. */
@Service
@RequiredArgsConstructor
public class DeviceCredentialService {

	private static final int SECRET_BYTES = 32;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final DeviceCredentialRepository repository;
	private final DeviceSecretHasher hasher;
	private final KstClock clock;

	/** 처음 등록하는 기기에만 토큰을 발급한다. 이미 등록된 기기면 재발급하지 않고 거절한다 (탈취한 ID로 가로채기 방지) */
	@Transactional
	public String register(String deviceId) {
		String secret = generateSecret();
		int inserted = repository.insertIgnore(deviceId, hasher.hash(secret), clock.now());
		if (inserted == 0) {
			throw new ApiException(ErrorCode.DEVICE_ALREADY_REGISTERED);
		}
		return secret;
	}

	public boolean verify(String deviceId, String secret) {
		if (deviceId == null || secret == null) {
			return false;
		}
		return repository.findById(deviceId)
				.map(credential -> hasher.matches(secret, credential.getSecretHash()))
				.orElse(false);
	}

	private static String generateSecret() {
		byte[] bytes = new byte[SECRET_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
