package com.oms.infrastructure.config;

import com.oms.application.port.output.OrderCachePort;
import com.oms.application.port.output.OrderEventPublisherPort;
import com.oms.application.port.output.OrderRepositoryPort;
import com.oms.application.service.CreateOrderService;
import com.oms.application.service.GetOrderService;
import com.oms.application.service.UpdateOrderStatusService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Unified Dependency Injection Configuration.
 * Instantiates pure application services and registers them within the Spring context.
 * This avoids coupling business services with framework-specific annotations like @Service.
 */
@Configuration
public class BeanConfig {

    @Bean
    public CreateOrderService createOrderService(
            OrderRepositoryPort orderRepositoryPort,
            OrderEventPublisherPort orderEventPublisherPort) {
        return new CreateOrderService(orderRepositoryPort, orderEventPublisherPort);
    }

    @Bean
    public GetOrderService getOrderService(
            OrderRepositoryPort orderRepositoryPort,
            OrderCachePort orderCachePort) {
        return new GetOrderService(orderRepositoryPort, orderCachePort);
    }

    @Bean
    public UpdateOrderStatusService updateOrderStatusService(
            OrderRepositoryPort orderRepositoryPort,
            OrderCachePort orderCachePort) {
        return new UpdateOrderStatusService(orderRepositoryPort, orderCachePort);
    }
}
