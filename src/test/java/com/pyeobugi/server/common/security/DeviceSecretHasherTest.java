package com.pyeobugi.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeviceSecretHasherTest {

	private final DeviceSecretHasher hasher = new DeviceSecretHasher();

	@Test
	void sameSecretSameHashAndNoRawSecretInside() {
		String hash = hasher.hash("my-secret-token");

		assertThat(hash).isEqualTo(hasher.hash("my-secret-token")).hasSize(64).matches("^[0-9a-f]+$");
		assertThat(hash).doesNotContain("my-secret-token");
		assertThat(hasher.hash("other-secret-token")).isNotEqualTo(hash);
	}

	@Test
	void matchesOnlyTheOriginalSecret() {
		String hash = hasher.hash("correct-secret");

		assertThat(hasher.matches("correct-secret", hash)).isTrue();
		assertThat(hasher.matches("wrong-secret", hash)).isFalse();
	}

}
