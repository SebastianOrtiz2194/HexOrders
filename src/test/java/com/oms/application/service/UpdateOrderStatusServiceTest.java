package com.oms.application.service;

import com.oms.application.port.output.OrderCachePort;
import com.oms.application.port.output.OrderRepositoryPort;
import com.oms.domain.exception.InvalidOrderException;
import com.oms.domain.exception.OrderNotFoundException;
import com.oms.domain.model.Order;
import com.oms.domain.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UpdateOrderStatusService.
 * Validates command orchestration, authoritative repository read, domain state transition, and cache eviction.
 */
@ExtendWith(MockitoExtension.class)
class UpdateOrderStatusServiceTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private OrderCachePort orderCachePort;

    @InjectMocks
    private UpdateOrderStatusService updateOrderStatusService;

    private UUID orderId;
    private Order dummyOrder;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        dummyOrder = Order.builder()
                .id(orderId)
                .customerName("Alice")
                .status(OrderStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("updateStatus() successfully transitions state, saves to repository, and evicts cache")
    void shouldUpdateStatusAndEvictCache() {
        // Arrange
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(dummyOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Order result = updateOrderStatusService.updateStatus(orderId, OrderStatus.CONFIRMED);

        // Assert
        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        verify(orderRepository, times(1)).findById(orderId);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(orderCachePort, times(1)).evict(orderId);
    }

    @Test
    @DisplayName("updateStatus() throws OrderNotFoundException when order does not exist in repository")
    void shouldThrowExceptionWhenOrderNotFound() {
        // Arrange
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(OrderNotFoundException.class, 
                () -> updateOrderStatusService.updateStatus(orderId, OrderStatus.CONFIRMED));

        verify(orderRepository, times(1)).findById(orderId);
        verify(orderRepository, never()).save(any());
        verify(orderCachePort, never()).evict(any());
    }

    @Test
    @DisplayName("updateStatus() throws InvalidOrderException on invalid transition without saving or evicting")
    void shouldThrowExceptionOnInvalidTransition() {
        // Arrange: dummyOrder is in PENDING, and PENDING cannot transition directly to DELIVERED
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(dummyOrder));

        // Act & Assert
        assertThrows(InvalidOrderException.class, 
                () -> updateOrderStatusService.updateStatus(orderId, OrderStatus.DELIVERED));

        verify(orderRepository, times(1)).findById(orderId);
        verify(orderRepository, never()).save(any());
        verify(orderCachePort, never()).evict(any());
    }
}
