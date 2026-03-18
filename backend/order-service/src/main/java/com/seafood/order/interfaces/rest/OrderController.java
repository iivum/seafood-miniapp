package com.seafood.order.interfaces.rest;

import com.seafood.order.application.OrderApplicationService;
import com.seafood.order.domain.model.Order;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "APIs for managing customer orders")
public class OrderController {
    private final OrderApplicationService orderApplicationService;

    @PostMapping
    @Operation(
        summary = "Create a new order",
        description = "Creates a new order with the provided details",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Order details",
            required = true,
            content = @Content(schema = @Schema(implementation = CreateOrderRequest.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Order created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
        }
    )
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderApplicationService.createOrder(request.getUserId(), request.getItems(), request.getTotalAmount(), request.getShippingAddress());
        return ResponseEntity.ok(order);
    }

    @GetMapping
    @Operation(
        summary = "List all orders",
        description = "Returns a list of all orders",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "204", description = "No orders found")
        }
    )
    public ResponseEntity<List<Order>> listAllOrders() {
        return ResponseEntity.ok(orderApplicationService.listAllOrders());
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get order by ID",
        description = "Returns a specific order by its ID",
        parameters = @Parameter(name = "id", description = "Order ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found")
        }
    )
    public ResponseEntity<Order> getOrderById(@PathVariable String id) {
        return orderApplicationService.getOrderById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    @Operation(
        summary = "Get orders by user ID",
        description = "Returns all orders for a specific user",
        parameters = @Parameter(name = "userId", description = "User ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Orders found"),
            @ApiResponse(responseCode = "204", description = "No orders found for this user")
        }
    )
    public ResponseEntity<List<Order>> getOrdersByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(orderApplicationService.getOrdersByUserId(userId));
    }

    @PutMapping("/{id}/status")
    @Operation(
        summary = "Update order status",
        description = "Updates the status of an existing order",
        parameters = @Parameter(name = "id", description = "Order ID", required = true),
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "New status",
            required = true,
            content = @Content(schema = @Schema(type = "string"))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Order status updated"),
            @ApiResponse(responseCode = "404", description = "Order not found")
        }
    )
    public ResponseEntity<Order> updateOrderStatus(@PathVariable String id, @RequestBody String status) {
        return ResponseEntity.ok(orderApplicationService.updateOrderStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete an order",
        description = "Deletes an order by its ID",
        parameters = @Parameter(name = "id", description = "Order ID", required = true),
        responses = {
            @ApiResponse(responseCode = "204", description = "Order deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Order not found")
        }
    )
    public ResponseEntity<Void> deleteOrder(@PathVariable String id) {
        orderApplicationService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pay")
    @Operation(
        summary = "Pay an order",
        description = "Processes payment for an existing order",
        parameters = @Parameter(name = "id", description = "Order ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Payment successful"),
            @ApiResponse(responseCode = "400", description = "Order cannot be paid"),
            @ApiResponse(responseCode = "404", description = "Order not found")
        }
    )
    public ResponseEntity<Order> payOrder(@PathVariable String id) {
        Order order = orderApplicationService.payOrder(id);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{id}/cancel")
    @Operation(
        summary = "Cancel an order",
        description = "Cancels an existing order",
        parameters = @Parameter(name = "id", description = "Order ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Order cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Order cannot be cancelled"),
            @ApiResponse(responseCode = "404", description = "Order not found")
        }
    )
    public ResponseEntity<Order> cancelOrder(@PathVariable String id) {
        Order order = orderApplicationService.cancelOrder(id);
        return ResponseEntity.ok(order);
    }
}