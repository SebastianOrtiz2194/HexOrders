package com.oms.infrastructure.config;

import com.oms.application.port.input.CreateOrderUseCase;
import com.oms.application.port.input.GetOrderUseCase;
import com.oms.application.port.input.UpdateOrderStatusUseCase;
import com.oms.application.port.output.OrderCachePort;
import com.oms.application.port.output.OrderEventPublisherPort;
import com.oms.application.port.output.OrderRepositoryPort;
import com.oms.application.service.CreateOrderService;
import com.oms.application.service.GetOrderService;
import com.oms.application.service.UpdateOrderStatusService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Unified Dependency Injection Configuration (Composition Root).
 * 
 * In Hexagonal Architecture, domain and application services remain strictly framework-free,
 * without '@Service' or '@Autowired' annotations.
 * 
 * This configuration class acts as the Composition Root:
 * 1. Injects driven/output port implementations into pure application services.
 * 2. Exposes application services to the Spring context strictly typed as Inbound Port interfaces
 *    (CreateOrderUseCase, GetOrderUseCase, UpdateOrderStatusUseCase).
 * 
 * Exposing port interfaces ensures driving adapters (e.g., REST controllers) adhere to the
 * Dependency Inversion Principle (DIP) and cannot bind to implementation details.
 */
@Configuration
public class BeanConfig {

    @Bean
    public CreateOrderUseCase createOrderUseCase(
            OrderRepositoryPort orderRepositoryPort,
            OrderEventPublisherPort orderEventPublisherPort) {
        return new CreateOrderService(orderRepositoryPort, orderEventPublisherPort);
    }

    @Bean
    public GetOrderUseCase getOrderUseCase(
            OrderRepositoryPort orderRepositoryPort,
            OrderCachePort orderCachePort) {
        return new GetOrderService(orderRepositoryPort, orderCachePort);
    }

    @Bean
    public UpdateOrderStatusUseCase updateOrderStatusUseCase(
            OrderRepositoryPort orderRepositoryPort,
            OrderCachePort orderCachePort) {
        return new UpdateOrderStatusService(orderRepositoryPort, orderCachePort);
    }
}
