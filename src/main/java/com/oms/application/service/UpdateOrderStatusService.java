package com.oms.application.service;

import com.oms.application.port.input.UpdateOrderStatusUseCase;
import com.oms.application.port.output.OrderCachePort;
import com.oms.application.port.output.OrderRepositoryPort;
import com.oms.domain.exception.OrderNotFoundException;
import com.oms.domain.model.Order;
import com.oms.domain.model.OrderStatus;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Primary orchestrator for the "Update Order Status" command.
 * 
 * Enforces Command-Query Responsibility Segregation (CQRS) and Single Responsibility Principle (SRP)
 * by separating write mutations from read-only query workflows.
 * 
 * Workflow:
 * 1. Fetches the authoritative order from the relational persistence port (never from cache, to avoid stale state).
 * 2. Delegates the state machine transition to the pure domain model (Order.updateStatus).
 * 3. Saves the mutated aggregate back through the persistence port.
 * 4. Evicts the stale cache entry to maintain cache consistency across the distributed system.
 * 
 * NOTE: Framework-agnostic pure Java class; instantiated as a Spring bean via BeanConfig.
 */
@RequiredArgsConstructor
public class UpdateOrderStatusService implements UpdateOrderStatusUseCase {

    private final OrderRepositoryPort orderRepository;
    private final OrderCachePort orderCachePort;

    @Override
    public Order updateStatus(UUID id, OrderStatus newStatus) {
        // 1. Fetch authoritative state directly from database to prevent mutating stale cached data
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order with ID " + id + " not found."));

        // 2. Delegate state transition validation directly to the domain aggregate root
        order.updateStatus(newStatus);

        // 3. Persist updated aggregate state
        Order saved = orderRepository.save(order);

        // 4. Evict stale cache entry
        orderCachePort.evict(id);

        return saved;
    }
}
