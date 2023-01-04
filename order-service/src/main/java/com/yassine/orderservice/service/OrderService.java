package com.yassine.orderservice.service;

import com.yassine.orderservice.dto.InventoryResponse;
import com.yassine.orderservice.dto.OrderLineItemsDto;
import com.yassine.orderservice.dto.OrderRequest;
import com.yassine.orderservice.event.OrderPlacedEvent;
import com.yassine.orderservice.model.Order;
import com.yassine.orderservice.model.OrderLineItems;
import com.yassine.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    private final Tracer tracer;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;


    public String placeOrder(OrderRequest orderRequest) {
        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList().stream().map(this::maptoOrderLineItems).toList();
        Order orderToCreate = Order.builder()
                .orderNumber(UUID.randomUUID().toString())
                .orderLineItemsList(orderLineItems)
                .build();
        List<String> skuCodes = orderLineItems.stream().map(OrderLineItems::getSkuCode).collect(Collectors.toList());

        Span inventoryServiceLookup = tracer.nextSpan().name("InventoryServiceLookup");
        try(Tracer.SpanInScope spanInScope = tracer.withSpan(inventoryServiceLookup.start())) {
            //Call Inventory service and place order if product is in stock
            InventoryResponse[] inventoryResponses = this.webClientBuilder.build().get()
                    .uri("http://inventory-service/api/inventory", uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                    .retrieve()
                    .bodyToMono(InventoryResponse[].class)
                    .block();
            boolean OrderElementsInStock = Arrays.stream(inventoryResponses).allMatch(InventoryResponse::getIsInStock);

            if(OrderElementsInStock){
                orderRepository.save(orderToCreate);
                kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(orderToCreate.getOrderNumber()));
                return "Order placed Successfully";
            } else {
                throw new IllegalArgumentException("Product is not in stock");
            }
        } finally {
            inventoryServiceLookup.end();
        }
    }

    private OrderLineItems maptoOrderLineItems(OrderLineItemsDto orderLineItemsDto) {
        return OrderLineItems.builder()
                .price(orderLineItemsDto.getPrice())
                .skuCode(orderLineItemsDto.getSkuCode())
                .quantity(orderLineItemsDto.getQuantity())
                .build();
    }
}
