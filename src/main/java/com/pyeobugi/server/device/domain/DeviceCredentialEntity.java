package com.pyeobugi.server.device.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * device_credentials 테이블 매핑. 등록은 DeviceCredentialRepository의 네이티브 INSERT IGNORE로만 한다.
 * 이미 등록된 기기는 재등록이 실패해야 하므로(탈취한 ID로 재등록해 토큰을 가로채지 못하게) save()는 쓰지 않는다.
 */
@Entity
@Table(name = "device_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceCredentialEntity {

	@Id
	private String deviceId;

	private String secretHash;

	private LocalDateTime createdAt;

}
