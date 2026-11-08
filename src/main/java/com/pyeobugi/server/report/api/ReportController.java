package com.pyeobugi.server.report.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pyeobugi.server.common.error.ErrorResponse;
import com.pyeobugi.server.common.filter.DeviceIdFilter;
import com.pyeobugi.server.report.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Report", description = "자세 리포트 조회")
@RestController
@RequiredArgsConstructor
public class ReportController {

	private final ReportService reportService;

	/** date를 생략하면 오늘. 형식 오류·없는 날짜는 400 INVALID_REQUEST (plan 7-3) */
	@Operation(summary = "일간·주간 리포트 조회", description = """
			요청 날짜 `D`의 하루·어제·이번 주·지난주 집계, 시간대별 유지율, 거북목 타임라인, 자세 분포, 코멘트를 돌려준다.

			- 그날 기록이 없으면 `hasData: false`, `day`는 0과 null, `distribution`·`comment`는 null이다.
			- 미래 날짜는 400 대신 기록 없는 날과 같은 빈 응답을 준다.
			- 응답 헤더 `Cache-Control: no-store`
			""")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	@ApiResponse(responseCode = "400", description = "`date` 형식 오류(`2026-1-5`)나 없는 날짜(`2026-02-30`), 또는 기기 ID 오류",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
					@ExampleObject(name = "INVALID_REQUEST",
							value = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 형식이 올바르지 않습니다.\",\"traceId\":\"req-7f3a9c2e\"}"),
					@ExampleObject(name = "DEVICE_ID_INVALID",
							value = "{\"code\":\"DEVICE_ID_INVALID\",\"message\":\"X-Device-Id 헤더가 없거나 UUID v4 형식이 아닙니다.\",\"traceId\":\"req-7f3a9c2e\"}")
			}))
	@ApiResponse(responseCode = "401", description = "`X-Device-Secret`이 없거나 일치하지 않음",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class),
					examples = @ExampleObject(
							value = "{\"code\":\"DEVICE_SECRET_INVALID\",\"message\":\"X-Device-Secret이 없거나 일치하지 않습니다.\",\"traceId\":\"req-7f3a9c2e\"}")))
	@ApiResponse(responseCode = "429", description = "호출 한도 초과. `retryAfter`초 후 재시도",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class),
					examples = @ExampleObject(
							value = "{\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"traceId\":\"req-7f3a9c2e\",\"retryAfter\":3}")))
	@GetMapping("/api/reports/summary")
	public ResponseEntity<ReportSummaryResponse> summary(
			@Parameter(hidden = true) @RequestAttribute(DeviceIdFilter.DEVICE_ID_ATTRIBUTE) String deviceId,
			@Parameter(description = "조회할 KST 날짜 `YYYY-MM-DD`. 생략하면 서버 기준 KST 오늘", example = "2026-10-04")
			@RequestParam(name = "date", required = false) String date) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(reportService.summarize(deviceId, date));
	}

}
