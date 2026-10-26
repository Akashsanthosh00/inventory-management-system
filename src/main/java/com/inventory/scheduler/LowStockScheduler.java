package com.inventory.scheduler;

import com.inventory.service.LowStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LowStockScheduler {

    private final LowStockService lowStockService;

    // Cron comes from app.low-stock.cron. If you run several instances, add ShedLock so only one runs the scan.
    @Scheduled(cron = "${app.low-stock.cron}")
    public void scanForLowStock() {
        int created = lowStockService.scan();
        log.info("Low-stock scan finished, {} new alert(s)", created);
    }
}
