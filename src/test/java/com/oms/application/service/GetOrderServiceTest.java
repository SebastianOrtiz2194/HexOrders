package com.oms.application.service;

import com.oms.application.port.output.OrderCachePort;
import com.oms.application.port.output.OrderRepositoryPort;
import com.oms.domain.exception.OrderNotFoundException;
import com.oms.domain.model.Order;
import com.oms.domain.model.OrderStatus;
import com.oms.domain.model.PagedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GetOrderService.
 * Validates order retrieval logic (Cache-Aside pattern) and status updates.
 */
@ExtendWith(MockitoExtension.class)
class GetOrderServiceTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private OrderCachePort orderCachePort;

    @InjectMocks
    private GetOrderService getOrderService;

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

    /**
     * Verifies that the service retrieves the order from the cache if it's available, 
     * bypassing the repository.
     */
    @Test
    void shouldReturnFromCacheWhenAvailable() {
        // Arrange
        when(orderCachePort.findById(orderId)).thenReturn(Optional.of(dummyOrder));

        // Act
        Order result = getOrderService.getOrderById(orderId);

        // Assert
        assertEquals(dummyOrder, result);
        verify(orderCachePort, times(1)).findById(orderId);
        verifyNoInteractions(orderRepository);
    }

    /**
     * Verifies that on a cache miss, the service retrieves the order from the repository 
     * and subsequently populates the cache.
     */
    @Test
    void shouldReturnFromRepoAndSaveInCacheWhenCacheMiss() {
        // Arrange
        when(orderCachePort.findById(orderId)).thenReturn(Optional.empty());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(dummyOrder));

        // Act
        Order result = getOrderService.getOrderById(orderId);

        // Assert
        assertEquals(dummyOrder, result);
        verify(orderCachePort, times(1)).findById(orderId);
        verify(orderRepository, times(1)).findById(orderId);
        verify(orderCachePort, times(1)).save(dummyOrder);
    }

    /**
     * Verifies that an OrderNotFoundException is thrown if the order doesn't exist 
     * in either the cache or the repository.
     */
    @Test
    void shouldThrowExceptionWhenOrderNotFoundInBoth() {
        // Arrange
        when(orderCachePort.findById(orderId)).thenReturn(Optional.empty());
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(OrderNotFoundException.class, () -> getOrderService.getOrderById(orderId));
    }

    /**
     * Verifies paginated retrieval of all orders from the repository.
     */
    @Test
    void shouldReturnAllOrdersFromRepo() {
        // Arrange
        List<Order> orders = List.of(dummyOrder);
        PagedResult<Order> pagedResult = new PagedResult<>(orders, 0, 20, 1, 1);
        when(orderRepository.findAll(0, 20, null)).thenReturn(pagedResult);

        // Act
        PagedResult<Order> result = getOrderService.getAllOrders(0, 20, null);

        // Assert
        assertEquals(1, result.content().size());
        assertEquals(dummyOrder, result.content().get(0));
        verify(orderRepository, times(1)).findAll(0, 20, null);
    }
}
