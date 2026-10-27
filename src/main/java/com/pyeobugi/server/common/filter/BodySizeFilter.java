package com.pyeobugi.server.common.filter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.pyeobugi.server.common.error.ErrorCode;
import com.pyeobugi.server.common.error.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * /api/** 요청 본문이 64KB를 넘으면 JSON 파싱 전에 413 PAYLOAD_TOO_LARGE로 응답한다 (plan 8-1).
 * Content-Length가 없는 요청(chunked)도 실제로 읽은 바이트 수로 판정한다.
 */
@Component
@Order(FilterOrders.BODY_SIZE)
@RequiredArgsConstructor
public class BodySizeFilter extends OncePerRequestFilter {

	public static final int MAX_BODY_BYTES = 64 * 1024;

	private final ErrorResponseWriter errorResponseWriter;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request.getContentLengthLong() > MAX_BODY_BYTES) {
			errorResponseWriter.write(response, ErrorCode.PAYLOAD_TOO_LARGE);
			return;
		}
		byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
		if (body.length > MAX_BODY_BYTES) {
			errorResponseWriter.write(response, ErrorCode.PAYLOAD_TOO_LARGE);
			return;
		}
		chain.doFilter(new CachedBodyRequest(request, body), response);
	}

	/** 이미 읽은 본문을 다시 읽을 수 있게 돌려준다 */
	private static final class CachedBodyRequest extends HttpServletRequestWrapper {

		private final byte[] body;

		CachedBodyRequest(HttpServletRequest request, byte[] body) {
			super(request);
			this.body = body;
		}

		@Override
		public ServletInputStream getInputStream() {
			InputStream in = new ByteArrayInputStream(body);
			return new ServletInputStream() {
				@Override
				public int read() throws IOException {
					return in.read();
				}

				@Override
				public int read(byte[] b, int off, int len) throws IOException {
					return in.read(b, off, len);
				}

				@Override
				public boolean isFinished() {
					return false;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener listener) {
					throw new UnsupportedOperationException();
				}
			};
		}

		@Override
		public int getContentLength() {
			return body.length;
		}

		@Override
		public long getContentLengthLong() {
			return body.length;
		}

	}

}
