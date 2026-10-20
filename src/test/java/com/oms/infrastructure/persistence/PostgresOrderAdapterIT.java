package com.oms.infrastructure.persistence;

import com.oms.application.port.output.OrderCachePort;
import com.oms.application.port.output.OrderEventPublisherPort;
import com.oms.domain.model.Order;
import com.oms.domain.model.OrderItem;
import com.oms.domain.model.OrderStatus;
import com.oms.infrastructure.adapter.output.persistence.PostgresOrderAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration Test — Persistence Layer.
 *
 * <p>Utilizes Testcontainers to spin up a live PostgreSQL instance in Docker. 
 * Redis and Kafka are mocked via {@code @MockBean} to isolate persistence-specific validation.</p>
 *
 * <p>{@code @SpringBootTest} initializes the full application context, ensuring 
 * that database migrations (Flyway) are correctly applied to the container 
 * before test execution.</p>
 */
@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresOrderAdapterIT {

    /**
     * Shared static container instance to optimize execution time across multiple test methods.
     */
    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("oms_db_test")
            .withUsername("test_user")
            .withPassword("test_password");

    /**
     * Dynamically maps container connection properties into the Spring Environment.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** Mocks the Redis cache adapter to simulate cache misses. */
    @MockBean
    private OrderCachePort orderCachePort;

    /** Mocks the Kafka publisher to prevent connection overhead during persistence tests. */
    @MockBean
    private OrderEventPublisherPort orderEventPublisherPort;

    @Autowired
    private PostgresOrderAdapter postgresOrderAdapter;

    // ─── Factory Methods ────────────────────────────────────────────────────────

    /**
     * Generates a sample domain Order, leveraging the actual aggregate root factory method.
     */
    private Order buildSampleOrder() {
        OrderItem item = OrderItem.builder()
                .productId("PROD-001")
                .productName("Laptop Pro")
                .quantity(2)
                .unitPrice(new BigDecimal("1299.99"))
                .build();

        return Order.create("Jane Doe", List.of(item));
    }

    // ─── Tests ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("save() persists an Order and its items correctly in PostgreSQL")
    void save_shouldPersistOrderWithItems() {
        // Given
        Order order = buildSampleOrder();

        // When
        Order saved = postgresOrderAdapter.save(order);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCustomerName()).isEqualTo("Jane Doe");
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("2599.98"));
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getProductId()).isEqualTo("PROD-001");
    }

    @Test
    @DisplayName("findById() returns the order when it exists in the database")
    void findById_whenOrderExists_shouldReturnOrder() {
        // Given
        Order saved = postgresOrderAdapter.save(buildSampleOrder());

        // When
        Optional<Order> found = postgresOrderAdapter.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getCustomerName()).isEqualTo("Jane Doe");
    }

    @Test
    @DisplayName("findById() returns empty Optional when order does not exist")
    void findById_whenOrderNotExists_shouldReturnEmpty() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        Optional<Order> result = postgresOrderAdapter.findById(nonExistentId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findAll() returns all persisted orders paginated")
    void findAll_shouldReturnAllPersistedOrders() {
        // Given
        postgresOrderAdapter.save(buildSampleOrder());
        postgresOrderAdapter.save(buildSampleOrder());

        // When
        var result = postgresOrderAdapter.findAll(0, 20, null);

        // Then
        assertThat(result.content()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.totalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("save() updates the status of an existing order")
    void save_shouldUpdateOrderStatus() {
        // Given
        Order saved = postgresOrderAdapter.save(buildSampleOrder());
        saved.updateStatus(OrderStatus.CONFIRMED);

        // When
        Order updated = postgresOrderAdapter.save(saved);

        // Then
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
