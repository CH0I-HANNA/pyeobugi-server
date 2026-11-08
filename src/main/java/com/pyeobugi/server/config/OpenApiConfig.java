package com.pyeobugi.server.config;

import java.util.List;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.pyeobugi.server.common.filter.DeviceIdFilter;
import com.pyeobugi.server.common.filter.DeviceSecretFilter;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;

/**
 * Swagger(OpenAPI) 공통 정보. 모든 /api/** 요청은 X-Device-Id와 X-Device-Secret 헤더가 필요하고,
 * 기기 등록(/api/devices/register)만 X-Device-Id 하나로 호출한다.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
		info = @Info(title = "펴부기 API", version = "v1", description = """
				펴부기 데스크톱 앱의 이벤트 수집과 자세 리포트 API.

				**인증 순서**
				1. 앱 첫 실행 때 UUID v4 기기 ID를 만들어 저장한다.
				2. `POST /api/devices/register`로 `deviceSecret`을 한 번만 발급받아 저장한다.
				3. 이후 모든 요청에 `X-Device-Id`와 `X-Device-Secret` 헤더를 같이 보낸다.

				**공통 규칙**
				- 날짜·시각은 모두 KST(`+09:00`), 초 단위다. 예: `2026-10-04T09:23:00+09:00`
				- 실패 응답은 모두 `{ code, message, traceId, retryAfter? }` 형식이다. 클라이언트는 `code`만 보고 처리한다.
				- `retryAfter`(초)는 429에서만 들어가고, 같은 값이 `Retry-After` 헤더에도 들어간다.
				- 호출 한도: 기기 ID당 분당 120회(순간 60회), IP당 분당 1,200회
				- 응답 필드는 값이 없어도 생략하지 않고 `null`로 내려준다 (명세에 null 가능으로 표시한 필드만)
				"""))
@SecurityScheme(name = OpenApiConfig.DEVICE_ID, type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER,
		paramName = DeviceIdFilter.HEADER,
		description = "기기 ID (UUID v4, 대소문자 무관). 없거나 형식이 틀리면 400 DEVICE_ID_INVALID")
@SecurityScheme(name = OpenApiConfig.DEVICE_SECRET, type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER,
		paramName = DeviceSecretFilter.HEADER,
		description = "기기 등록 때 받은 deviceSecret. 없거나 일치하지 않으면 401 DEVICE_SECRET_INVALID")
public class OpenApiConfig {

	public static final String DEVICE_ID = "deviceId";
	public static final String DEVICE_SECRET = "deviceSecret";

	/**
	 * 어노테이션으로 표현할 수 없는 두 가지를 고친다.
	 * - 헤더 두 개를 모두 요구 (@SecurityRequirement를 나열하면 둘 중 하나만 요구하는 뜻이 된다)
	 * - null 가능한 객체 필드: springdoc이 { type: null, $ref }로 만들어 객체가 사라지므로 anyOf [$ref, null]로 바꾼다
	 */
	@Bean
	public OpenApiCustomizer openApiCustomizer() {
		return openApi -> {
			openApi.setSecurity(List.of(new SecurityRequirement().addList(DEVICE_ID).addList(DEVICE_SECRET)));
			openApi.getComponents().getSchemas().values().forEach(OpenApiConfig::fixNullableRefs);
		};
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void fixNullableRefs(Schema schema) {
		if (schema.getProperties() == null) {
			return;
		}
		schema.getProperties().replaceAll((name, value) -> {
			Schema property = (Schema) value;
			if (property.get$ref() == null || property.getTypes() == null || !property.getTypes().contains("null")) {
				return property;
			}
			return new Schema<>()
					.description(property.getDescription())
					.anyOf(List.of(new Schema<>().$ref(property.get$ref()), new Schema<>().types(Set.of("null"))));
		});
	}

}
