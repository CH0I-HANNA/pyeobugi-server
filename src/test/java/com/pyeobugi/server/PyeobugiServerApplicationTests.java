package com.pyeobugi.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PyeobugiServerApplicationTests {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void jvmTimeZoneIsKst() {
		assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Seoul");
	}

	@Test
	void dbSessionTimeZoneIsKst() {
		String timeZone = jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class);
		assertThat(timeZone).isEqualTo("Asia/Seoul");
	}

}
