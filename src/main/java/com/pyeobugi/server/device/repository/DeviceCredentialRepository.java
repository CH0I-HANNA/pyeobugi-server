package com.pyeobugi.server.device.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pyeobugi.server.device.domain.DeviceCredentialEntity;

public interface DeviceCredentialRepository extends JpaRepository<DeviceCredentialEntity, String> {

	/** 반환값: 1 = 새로 등록, 0 = 이미 등록된 기기. 재등록을 막아야 탈취한 ID로 토큰을 새로 발급받지 못한다 */
	@Modifying
	@Query(value = """
			INSERT IGNORE INTO device_credentials (device_id, secret_hash, created_at)
			VALUES (:deviceId, :secretHash, :createdAt)
			""", nativeQuery = true)
	int insertIgnore(@Param("deviceId") String deviceId,
			@Param("secretHash") String secretHash,
			@Param("createdAt") LocalDateTime createdAt);

}
