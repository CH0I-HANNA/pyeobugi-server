package com.pyeobugi.server.common.filter;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.pyeobugi.server.common.error.ErrorCode;
import com.pyeobugi.server.common.error.ErrorResponseWriter;
import com.pyeobugi.server.device.service.DeviceCredentialService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * X-Device-Secret이 등록된 기기의 토큰과 일치하는지 검사한다. 기기 ID만으로는 누구나 그 기기 행세를 할 수 있던
 * 문제(탈취)를 보완한다. 등록 엔드포인트(/api/devices/register) 자체는 아직 토큰이 없으므로 검사하지 않는다.
 */
@Component
@Order(FilterOrders.DEVICE_SECRET)
@RequiredArgsConstructor
public class DeviceSecretFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Device-Secret";
	private static final String REGISTER_PATH = "/api/devices/register";

	private final DeviceCredentialService deviceCredentialService;
	private final ErrorResponseWriter errorResponseWriter;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/")
				|| HttpMethod.OPTIONS.matches(request.getMethod())
				|| request.getRequestURI().equals(REGISTER_PATH);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String deviceId = (String) request.getAttribute(DeviceIdFilter.DEVICE_ID_ATTRIBUTE);
		String secret = request.getHeader(HEADER);
		if (!deviceCredentialService.verify(deviceId, secret)) {
			errorResponseWriter.write(response, ErrorCode.DEVICE_SECRET_INVALID);
			return;
		}
		chain.doFilter(request, response);
	}

}
