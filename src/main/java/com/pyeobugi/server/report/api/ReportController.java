package com.pyeobugi.server.report.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pyeobugi.server.common.filter.DeviceIdFilter;
import com.pyeobugi.server.report.service.ReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ReportController {

	private final ReportService reportService;

	/** date를 생략하면 오늘. 형식 오류·없는 날짜는 400 INVALID_REQUEST (plan 7-3) */
	@GetMapping("/api/reports/summary")
	public ResponseEntity<ReportSummaryResponse> summary(
			@RequestAttribute(DeviceIdFilter.DEVICE_ID_ATTRIBUTE) String deviceId,
			@RequestParam(name = "date", required = false) String date) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(reportService.summarize(deviceId, date));
	}

}
