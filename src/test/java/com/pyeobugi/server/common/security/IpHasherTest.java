package com.pyeobugi.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IpHasherTest {

	private static final String SECRET = "test-secret-0123456789abcdefghijklmn";

	@Test
	void sameIpSameHashAndNoRawIpInside() {
		IpHasher hasher = new IpHasher(SECRET);

		String hash = hasher.hash("203.0.113.7");

		assertThat(hash).isEqualTo(hasher.hash("203.0.113.7")).hasSize(64).matches("^[0-9a-f]+$");
		assertThat(hash).doesNotContain("203.0.113.7");
		assertThat(hasher.hash("203.0.113.8")).isNotEqualTo(hash);
	}

	@Test
	void differentSecretGivesDifferentHash() {
		assertThat(new IpHasher(SECRET).hash("203.0.113.7"))
				.isNotEqualTo(new IpHasher(SECRET + "x").hash("203.0.113.7"));
	}

	@Test
	void shortOrMissingSecretFailsFast() {
		assertThatThrownBy(() -> new IpHasher("short")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new IpHasher(null)).isInstanceOf(IllegalStateException.class);
	}

}
