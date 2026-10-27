package com.pyeobugi.server.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate Limit 수치 (plan 8-7). application.yml의 pyeobugi.rate-limit에서 읽는다.
 *
 * @param device     기기 ID별 한도
 * @param ip         IP 해시별 한도 (학교 Wi-Fi처럼 여러 기기가 같은 IP를 쓰는 경우를 고려해 크게)
 * @param bucketIdle 이 시간 동안 요청이 없는 버킷은 메모리에서 지운다
 */
@ConfigurationProperties("pyeobugi.rate-limit")
public record RateLimitProperties(Limit device, Limit ip, Duration bucketIdle) {

	/**
	 * @param capacity        순간 최대 요청 수 (버킷 크기)
	 * @param refillPerMinute 분당 채워지는 요청 수
	 */
	public record Limit(long capacity, long refillPerMinute) {
	}

}
