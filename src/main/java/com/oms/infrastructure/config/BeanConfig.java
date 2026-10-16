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
