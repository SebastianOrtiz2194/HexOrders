package com.oms.application.service;

import com.oms.application.port.input.GetOrderUseCase;
import com.oms.application.port.output.OrderCachePort;
import com.oms.application.port.output.OrderRepositoryPort;
import com.oms.domain.exception.OrderNotFoundException;
import com.oms.domain.model.Order;
import com.oms.domain.model.OrderStatus;
import com.oms.domain.model.PagedResult;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Orchestrates order queries (retrieval only).
 * Follows CQRS by isolating read operations from write mutations.
 * Implements a 'Cache-Aside' pattern: We attempt to fetch the order from the cache (e.g., Redis). 
 * On a cache miss, we read from the primary database and subsequently populate the cache.
 */
@RequiredArgsConstructor
public class GetOrderService implements GetOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final OrderCachePort orderCachePort;

    @Override
    public Order getOrderById(UUID id) {
        // 1. First, check the cache port
        return orderCachePort.findById(id)
                .orElseGet(() -> {
                    // 2. Fallback: Search in the primary relational DB
                    Order orderFromDb = orderRepository.findById(id)
                            .orElseThrow(() -> new OrderNotFoundException("Order with ID " + id + " not found."));
                    
                    // 3. Populate the cache with the newly retrieved database record
                    orderCachePort.save(orderFromDb);
                    return orderFromDb;
                });
    }

    @Override
    public PagedResult<Order> getAllOrders(int page, int size, OrderStatus status) {
        return orderRepository.findAll(page, size, status);
    }
}
