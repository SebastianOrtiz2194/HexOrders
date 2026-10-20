package com.oms.application.service;

import com.oms.application.port.input.CreateOrderUseCase;
import com.oms.application.port.output.OrderEventPublisherPort;
import com.oms.application.port.output.OrderRepositoryPort;
import com.oms.domain.event.OrderCreatedEvent;
import com.oms.domain.model.Order;
import lombok.RequiredArgsConstructor;

/**
 * Primary orchestrator for the "Create Order" workflow.
 * 
 * Workflow:
 * 1. Delegates to the pure domain model factory (Order.create) to enforce business rules and invariants.
 * 2. Commands the repository output port to persist the object.
 * 3. Publishes a Domain Event to notify downstream sub-systems (e.g., via Kafka).
 * 
 * NOTE: We do not use Spring's '@Service' annotation here to maintain the 
 * technology-agnostic purity of the application service layer.
 */
@RequiredArgsConstructor
public class CreateOrderService implements CreateOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final OrderEventPublisherPort eventPublisher;

    @Override
    public Order createOrder(Order orderCommand) {
        // 1. Domain: Validate business invariants and instantiate pristine aggregate via Domain Factory
        Order order = (orderCommand.getId() != null && orderCommand.getStatus() != null)
                ? orderCommand
                : Order.create(orderCommand.getCustomerName(), orderCommand.getItems());
        
        // 2. Secondary persistence port
        Order savedOrder = orderRepository.save(order);
        
        // 3. Local generation of the Async Domain Event
        OrderCreatedEvent event = new OrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getCustomerName(),
                savedOrder.getTotalAmount(),
                savedOrder.getStatus().name(),
                savedOrder.getCreatedAt(),
                savedOrder.getUpdatedAt(),
                savedOrder.getItems().stream()
                        .map(item -> new OrderCreatedEvent.OrderItemEvent(
                                item.getProductId(),
                                item.getProductName(),
                                item.getQuantity(),
                                item.getUnitPrice()
                        ))
                        .toList()
        );
        eventPublisher.publish(event);
        
        return savedOrder;
    }
}
