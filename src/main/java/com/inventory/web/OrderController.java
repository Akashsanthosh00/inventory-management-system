package com.inventory.web;

import com.inventory.domain.OrderStatus;
import com.inventory.dto.OrderResponse;
import com.inventory.dto.PageResponse;
import com.inventory.dto.PlaceOrderRequest;
import com.inventory.security.AuthUser;
import com.inventory.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Place an order",
            description = "Returns 202 with status PENDING. Stock is reserved asynchronously via RabbitMQ; "
                    + "poll GET /api/orders/{id} to see CONFIRMED or REJECTED.")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderResponse place(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(user.tenantId(), user.email(), request);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return orderService.get(user.tenantId(), id);
    }

    @GetMapping
    public PageResponse<OrderResponse> list(@AuthenticationPrincipal AuthUser user,
                                            @RequestParam(required = false) OrderStatus status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return orderService.list(user.tenantId(), status,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id")));
    }

    @Operation(summary = "Cancel a PENDING or CONFIRMED order (confirmed orders return their stock)")
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return orderService.cancel(user.tenantId(), user.email(), id);
    }
}
