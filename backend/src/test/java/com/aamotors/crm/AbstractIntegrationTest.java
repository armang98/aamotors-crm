package com.aamotors.crm;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Shared base for integration tests. One PostgreSQL container per JVM; Flyway
 * runs the real migrations against it, so tests exercise the production schema.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // Singleton container: started once per JVM and shared by every test class, so the
    // cached Spring context keeps a live database. Ryuk reaps it when the JVM exits.
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("aamotors_test")
            .withUsername("test")
            .withPassword("test");

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    static {
        POSTGRES.start();
        RABBIT.start();
    }

    protected static final String ADMIN_USERNAME = "admin";
    protected static final String ADMIN_PASSWORD = "test-password-123";
}
