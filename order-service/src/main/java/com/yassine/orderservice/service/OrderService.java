package com.yassine.orderservice.service;

import com.yassine.orderservice.dto.InventoryResponse;
import com.yassine.orderservice.dto.OrderLineItemsDto;
import com.yassine.orderservice.dto.OrderRequest;
import com.yassine.orderservice.model.Order;
import com.yassine.orderservice.model.OrderLineItems;
import com.yassine.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
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


    public void placeOrder(OrderRequest orderRequest) {
        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList().stream().map(this::maptoOrderLineItems).toList();
        Order orderToCreate = Order.builder()
                .orderNumber(UUID.randomUUID().toString())
                .orderLineItemsList(orderLineItems)
                .build();
        List<String> skuCodes = orderLineItems.stream().map(OrderLineItems::getSkuCode).collect(Collectors.toList());

        //Call Inventory service and place order if product is in stock
        InventoryResponse[] inventoryResponses = this.webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory", uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();
        boolean OrderElementsInStock = Arrays.stream(inventoryResponses).allMatch(InventoryResponse::getIsInStock);

        if(OrderElementsInStock){
            orderRepository.save(orderToCreate);
        } else {
            throw new IllegalArgumentException("Product is not in stock");
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
