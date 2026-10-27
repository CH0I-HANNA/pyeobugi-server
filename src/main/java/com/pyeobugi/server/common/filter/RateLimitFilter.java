package com.pyeobugi.server.common.filter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pyeobugi.server.common.error.ErrorCode;
import com.pyeobugi.server.common.error.ErrorResponse;
import com.pyeobugi.server.common.error.ErrorResponseWriter;
import com.pyeobugi.server.common.security.IpHasher;
import com.pyeobugi.server.config.RateLimitProperties;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * /api/** 요청을 IP 해시별, 기기 ID별 토큰 버킷으로 제한한다 (plan 8-7). 서버 1대 기준 인메모리.
 * 초과 시 429 RATE_LIMITED + 본문 retryAfter(초) + Retry-After 헤더.
 * DeviceIdFilter 뒤에서 실행되므로 기기 ID는 이미 검증·소문자 정규화되어 있다.
 */
@Slf4j
@Component
@Order(FilterOrders.RATE_LIMIT)
public class RateLimitFilter extends OncePerRequestFilter {

	private final RateLimitProperties properties;
	private final IpHasher ipHasher;
	private final ErrorResponseWriter errorResponseWriter;
	private final Cache<String, Bucket> deviceBuckets;
	private final Cache<String, Bucket> ipBuckets;

	public RateLimitFilter(RateLimitProperties properties, IpHasher ipHasher, ErrorResponseWriter errorResponseWriter) {
		this.properties = properties;
		this.ipHasher = ipHasher;
		this.errorResponseWriter = errorResponseWriter;
		this.deviceBuckets = newCache(properties.bucketIdle());
		this.ipBuckets = newCache(properties.bucketIdle());
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/")
				|| HttpMethod.OPTIONS.matches(request.getMethod());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Bucket ipBucket = ipBuckets.get(ipHasher.hash(request.getRemoteAddr()), k -> newBucket(properties.ip()));
		ConsumptionProbe ip = ipBucket.tryConsumeAndReturnRemaining(1);
		if (!ip.isConsumed()) {
			reject(response, ip, "ip");
			return;
		}
		String deviceId = (String) request.getAttribute(DeviceIdFilter.DEVICE_ID_ATTRIBUTE);
		ConsumptionProbe device = deviceBuckets.get(deviceId, k -> newBucket(properties.device()))
				.tryConsumeAndReturnRemaining(1);
		if (!device.isConsumed()) {
			reject(response, device, "device");
			return;
		}
		chain.doFilter(request, response);
	}

	private void reject(HttpServletResponse response, ConsumptionProbe probe, String limitedBy) throws IOException {
		int retryAfter = retryAfterSeconds(probe);
		// IP와 기기 ID는 로그에 남기지 않는다 (기기 ID 앞 8자리는 요청 로그에 이미 있음)
		log.warn("Rate limit 초과 by={} retryAfter={}s", limitedBy, retryAfter);
		response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
		errorResponseWriter.write(response, ErrorCode.RATE_LIMITED.getStatus().value(),
				ErrorResponse.rateLimited(retryAfter));
	}

	/** 다음 토큰이 채워질 때까지 남은 시간을 초 단위로 올림, 최소 1초 */
	static int retryAfterSeconds(ConsumptionProbe probe) {
		long seconds = (probe.getNanosToWaitForRefill() + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1);
		return (int) Math.max(1, seconds);
	}

	private static Bucket newBucket(RateLimitProperties.Limit limit) {
		return Bucket.builder()
				.addLimit(Bandwidth.builder()
						.capacity(limit.capacity())
						.refillGreedy(limit.refillPerMinute(), Duration.ofMinutes(1))
						.build())
				.build();
	}

	private static Cache<String, Bucket> newCache(Duration idle) {
		return Caffeine.newBuilder().expireAfterAccess(idle).maximumSize(100_000).build();
	}

}
