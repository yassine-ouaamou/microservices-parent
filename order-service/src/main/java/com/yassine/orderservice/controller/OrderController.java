package com.yassine.orderservice.controller;


import com.yassine.orderservice.dto.OrderRequest;
import com.yassine.orderservice.service.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CircuitBreaker(name= "inventory", fallbackMethod = "fallBackMethod")
    //@TimeLimiter(name = "inventory")
    //@Retry(name = "inventory")
    public CompletableFuture<String> placeOrder(@RequestBody OrderRequest orderRequest) {
        return CompletableFuture.supplyAsync(() -> this.orderService.placeOrder(orderRequest));
    }

    public CompletableFuture<String> fallBackMethod(OrderRequest orderRequest, RuntimeException runtimeException) {
        return CompletableFuture.supplyAsync(()-> "Something went wrong! Please order in a few minutes");
    }
}
