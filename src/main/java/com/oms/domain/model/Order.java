package com.oms.domain.model;

import com.oms.domain.exception.InvalidOrderException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The 'Order' entity and Aggregate Root for our order management domain.
 * As an Aggregate Root, it acts as the single point of interaction for the domain model.
 * It is free from framework annotations like '@Entity'. State manipulation is strictly 
 * controlled through constructors and pure Java behavior methods to ensure consistency.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    
    private UUID id;
    private String customerName;
    private List<OrderItem> items;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Domain Factory Method: Creates a new, fully validated and initialized Order aggregate root.
     * Enforces the DDD rule that an aggregate can never exist in an uninitialized or invalid state.
     *
     * @param customerName Name of the customer placing the order.
     * @param items List of items in the order.
     * @return A valid Order aggregate in PENDING status with an ID, timestamps, and total amount.
     * @throws InvalidOrderException if any invariant is violated.
     */
    public static Order create(String customerName, List<OrderItem> items) {
        validateCustomerName(customerName);
        validateItems(items);

        List<OrderItem> defensiveItems = List.copyOf(items);
        BigDecimal total = calculateTotal(defensiveItems);
        LocalDateTime now = LocalDateTime.now();

        return Order.builder()
                .id(UUID.randomUUID())
                .customerName(customerName.trim())
                .items(defensiveItems)
                .status(OrderStatus.PENDING)
                .totalAmount(total)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Domain behavior: Calculates the total amount of the order based on its items
     * and forces the initial state to PENDING when validating an incoming order request.
     * <p>
     * We avoid using standard "Setters" here. Any state mutation goes through 
     * explicit domain methods that enforce consistency and business rules (e.g., updateStatus()).
     *
     * @deprecated Prefer the static factory method {@link #create(String, List)} 
     * to prevent creating anemic or uninitialized aggregate instances.
     */
    @Deprecated(since = "1.1")
    public void validateAndInitialize() {
        validateCustomerName(this.customerName);
        validateItems(this.items);

        this.customerName = this.customerName.trim();
        this.status = OrderStatus.PENDING;
        
        // Sum all item subtotals using Java Streams
        this.totalAmount = calculateTotal(this.items);
        
        // Assign a default ID if none exists. This proves valuable when the 
        // JPA repository attempts to persist the entity by treating it as a new record.
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    private static void validateCustomerName(String customerName) {
        if (customerName == null || customerName.trim().isEmpty()) {
            throw new InvalidOrderException("Customer name cannot be empty");
        }
    }

    private static void validateItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new InvalidOrderException("Order must have at least one item");
        }
        items.forEach(OrderItem::validate);
    }

    private static BigDecimal calculateTotal(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::calculateSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Behavior mutator (Raw setters are not used).
     * In Domain-Driven Design (DDD), these types of methods encapsulate the 
     * progression of the order's state (acting like a finite state machine).
     *
     * @param newStatus The target state for the order transition.
     * @throws InvalidOrderException if the transition is explicitly prohibited.
     */
    public void updateStatus(OrderStatus newStatus) {
        if (newStatus == null) {
            throw new InvalidOrderException("New status cannot be null");
        }
        if (!this.status.canTransitionTo(newStatus)) {
            throw new InvalidOrderException(String.format("Invalid status transition from %s to %s", this.status, newStatus));
        }
        
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }
}
