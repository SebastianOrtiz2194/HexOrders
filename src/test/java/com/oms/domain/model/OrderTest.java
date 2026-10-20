package com.oms.domain.model;

import com.oms.domain.exception.InvalidOrderException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Order Aggregate Root.
 * Validates core business rules, state transitions, factory methods, and initialization logic.
 */
class OrderTest {

    @Test
    void shouldSuccessfullyInitializeValidOrder() {
        // Arrange
        OrderItem item = OrderItem.builder()
                .productId("sku-123")
                .quantity(2)
                .unitPrice(new BigDecimal("50.00"))
                .build();
        
        Order order = Order.builder()
                .customerName("John Doe")
                .items(List.of(item))
                .build();

        // Act
        order.validateAndInitialize();

        // Assert
        assertNotNull(order.getId(), "The 'id' should be automatically generated if null during initialization.");
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(new BigDecimal("100.00"), order.getTotalAmount());
    }

    @Test
    void shouldThrowExceptionWhenNoItems() {
        // Arrange
        Order orderWithEmptyItems = Order.builder()
                .customerName("Jane Doe")
                .items(Collections.emptyList())
                .build();
        
        Order orderWithNullItems = Order.builder()
                .customerName("Jake Doe")
                .items(null)
                .build();

        // Act & Assert
        Exception e1 = assertThrows(InvalidOrderException.class, orderWithEmptyItems::validateAndInitialize);
        assertEquals("Order must have at least one item", e1.getMessage());

        assertThrows(InvalidOrderException.class, orderWithNullItems::validateAndInitialize);
    }
    
    @Test
    void shouldThrowExceptionOnEmptyCustomerName() {
        // Arrange
        Order emptyNameOrder = Order.builder()
                .customerName("  ")
                .items(List.of(OrderItem.builder()
                        .productId("sku-1")
                        .quantity(1)
                        .unitPrice(BigDecimal.TEN)
                        .build()))
                .build();

        // Act & Assert
        assertThrows(InvalidOrderException.class, emptyNameOrder::validateAndInitialize);
    }
    
    @Test
    void shouldHandleStatusTransitionsCorrectly() {
        // Arrange
        Order order = Order.builder()
                .customerName("Jane")
                .items(List.of(OrderItem.builder()
                        .productId("sku-1")
                        .quantity(1)
                        .unitPrice(BigDecimal.TEN)
                        .build()))
                .build();
        
        order.validateAndInitialize();
        assertEquals(OrderStatus.PENDING, order.getStatus());
        
        // Act & Assert: Valid PENDING -> CONFIRMED transition
        order.updateStatus(OrderStatus.CONFIRMED);
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertNotNull(order.getUpdatedAt());
        
        // Act & Assert: Invalid CONFIRMED -> PENDING transition (regression check)
        assertThrows(InvalidOrderException.class, () -> order.updateStatus(OrderStatus.PENDING));
        
        // Act & Assert: Valid CONFIRMED -> SHIPPED transition
        order.updateStatus(OrderStatus.SHIPPED);
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        
        // Act & Assert: Valid SHIPPED -> DELIVERED transition
        order.updateStatus(OrderStatus.DELIVERED);
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        
        // Act & Assert: Invalid DELIVERED -> CANCELLED transition (terminal state check)
        assertThrows(InvalidOrderException.class, () -> order.updateStatus(OrderStatus.CANCELLED));
    }

    // ─── Item validation (fail fast) ─────────────────────────────────────────────

    private Order orderWithItem(OrderItem item) {
        return Order.builder()
                .customerName("Valid Customer")
                .items(List.of(item))
                .build();
    }

    @Test
    void shouldRejectItemWithNullQuantity() {
        OrderItem item = OrderItem.builder()
                .productId("sku-1")
                .quantity(null)
                .unitPrice(BigDecimal.TEN)
                .build();

        Exception ex = assertThrows(InvalidOrderException.class,
                () -> orderWithItem(item).validateAndInitialize());
        assertEquals("Quantity must be strictly positive", ex.getMessage());
    }

    @Test
    void shouldRejectItemWithZeroOrNegativeQuantity() {
        assertThrows(InvalidOrderException.class, () -> orderWithItem(
                OrderItem.builder().productId("sku-1").quantity(0).unitPrice(BigDecimal.TEN).build())
                .validateAndInitialize());
        assertThrows(InvalidOrderException.class, () -> orderWithItem(
                OrderItem.builder().productId("sku-1").quantity(-3).unitPrice(BigDecimal.TEN).build())
                .validateAndInitialize());
    }

    @Test
    void shouldRejectItemWithNullUnitPrice() {
        OrderItem item = OrderItem.builder()
                .productId("sku-1")
                .quantity(2)
                .unitPrice(null)
                .build();

        Exception ex = assertThrows(InvalidOrderException.class,
                () -> orderWithItem(item).validateAndInitialize());
        assertEquals("Unit price must be strictly positive", ex.getMessage());
    }

    @Test
    void shouldRejectItemWithZeroOrNegativeUnitPrice() {
        assertThrows(InvalidOrderException.class, () -> orderWithItem(
                OrderItem.builder().productId("sku-1").quantity(2).unitPrice(BigDecimal.ZERO).build())
                .validateAndInitialize());
        assertThrows(InvalidOrderException.class, () -> orderWithItem(
                OrderItem.builder().productId("sku-1").quantity(2).unitPrice(new BigDecimal("-5.00")).build())
                .validateAndInitialize());
    }

    @Test
    void shouldRejectItemWithBlankProductId() {
        OrderItem item = OrderItem.builder()
                .productId("   ")
                .quantity(1)
                .unitPrice(BigDecimal.TEN)
                .build();

        Exception ex = assertThrows(InvalidOrderException.class,
                () -> orderWithItem(item).validateAndInitialize());
        assertEquals("Product ID cannot be blank", ex.getMessage());
    }

    @Test
    void shouldFailLoudlyWhenComputingSubtotalOnUnvalidatedItem() {
        assertThrows(IllegalStateException.class,
                () -> OrderItem.builder().build().calculateSubtotal());
    }

    // ─── Domain Factory Method (Order.create) ───────────────────────────────────

    @Test
    void shouldSuccessfullyCreateValidOrderUsingFactoryMethod() {
        OrderItem item = OrderItem.builder()
                .productId("sku-123")
                .productName("Mechanical Keyboard")
                .quantity(2)
                .unitPrice(new BigDecimal("50.00"))
                .build();

        Order order = Order.create("John Doe", List.of(item));

        assertNotNull(order.getId());
        assertEquals("John Doe", order.getCustomerName());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(new BigDecimal("100.00"), order.getTotalAmount());
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
        assertEquals(1, order.getItems().size());
        assertEquals("sku-123", order.getItems().get(0).getProductId());
    }

    @Test
    void shouldThrowExceptionWhenCreatingOrderWithEmptyCustomerName() {
        OrderItem item = OrderItem.builder()
                .productId("sku-1")
                .quantity(1)
                .unitPrice(BigDecimal.TEN)
                .build();

        assertThrows(InvalidOrderException.class, () -> Order.create("", List.of(item)));
        assertThrows(InvalidOrderException.class, () -> Order.create("   ", List.of(item)));
        assertThrows(InvalidOrderException.class, () -> Order.create(null, List.of(item)));
    }

    @Test
    void shouldThrowExceptionWhenCreatingOrderWithNullOrEmptyItems() {
        assertThrows(InvalidOrderException.class, () -> Order.create("Alice", Collections.emptyList()));
        assertThrows(InvalidOrderException.class, () -> Order.create("Alice", null));
    }

    @Test
    void shouldThrowExceptionWhenCreatingOrderWithInvalidItem() {
        OrderItem invalidItem = OrderItem.builder()
                .productId("sku-1")
                .quantity(-1)
                .unitPrice(BigDecimal.TEN)
                .build();

        assertThrows(InvalidOrderException.class, () -> Order.create("Alice", List.of(invalidItem)));
    }

    @Test
    void shouldMakeDefensiveCopyOfItemsOnCreation() {
        OrderItem item = OrderItem.builder()
                .productId("sku-1")
                .quantity(1)
                .unitPrice(BigDecimal.TEN)
                .build();

        List<OrderItem> mutableList = new ArrayList<>();
        mutableList.add(item);

        Order order = Order.create("Alice", mutableList);
        assertEquals(1, order.getItems().size());

        // Modifying the external list must not affect the aggregate's internal state
        mutableList.add(OrderItem.builder()
                .productId("sku-2")
                .quantity(3)
                .unitPrice(BigDecimal.ONE)
                .build());

        assertEquals(1, order.getItems().size());
    }
}
