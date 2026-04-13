package com.gitbyul.sso_bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class SsoBootstrapApplicationTests {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:16-alpine")
					.withDatabaseName("sso_platform")
					.withUsername("sso")
					.withPassword("sso");

	@Container
	static final GenericContainer<?> REDIS =
			new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

	@DynamicPropertySource
	static void dataProps(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
		r.add("spring.data.redis.host", REDIS::getHost);
		r.add("spring.data.redis.port", REDIS::getFirstMappedPort);
	}

	@Test
	void contextLoads() {
	}
}
