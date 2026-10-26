package com.inventory.web;

import com.inventory.dto.AlertResponse;
import com.inventory.dto.PageResponse;
import com.inventory.security.AuthUser;
import com.inventory.service.LowStockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts")
public class AlertController {

    private final LowStockService lowStockService;

    @Operation(summary = "Low-stock alerts raised by the scheduled scan (open by default)")
    @GetMapping
    public PageResponse<AlertResponse> list(@AuthenticationPrincipal AuthUser user,
                                            @RequestParam(defaultValue = "false") boolean resolved,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return lowStockService.list(user.tenantId(), resolved,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }
}
