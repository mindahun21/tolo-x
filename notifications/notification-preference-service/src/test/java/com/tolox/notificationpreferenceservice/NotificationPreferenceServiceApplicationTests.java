package com.tolox.notificationpreferenceservice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test for Spring Context initialization.
 * Currently disabled because integration tests require external infrastructure (Postgres, Redis, Kafka).
 * These will be enabled once Testcontainers are configured.
 */
@Disabled("Integration test requires external infrastructure (Postgres, Redis, Kafka)")
@SpringBootTest
class NotificationPreferenceServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
