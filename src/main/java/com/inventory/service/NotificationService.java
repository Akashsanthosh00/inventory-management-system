package com.inventory.service;

import com.inventory.domain.LowStockAlert;
import com.inventory.messaging.OrderStatusMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Notification sink. Currently logs; swap the bodies for SES / SNS / SMTP / WhatsApp without touching callers.
 */
@Slf4j
@Service
public class NotificationService {

    public void sendOrderStatus(OrderStatusMessage m) {
        log.info("[NOTIFY] tenant={} order={} status={} customer={} reason={}",
                m.tenantId(), m.orderNumber(), m.status(), m.customerEmail(), m.reason());
    }

    public void sendLowStock(LowStockAlert a) {
        log.warn("[LOW STOCK] tenant={} product={} ({}) warehouse={} quantity={} threshold={}",
                a.getTenantId(), a.getProductSku(), a.getProductName(), a.getWarehouseName(),
                a.getQuantity(), a.getThreshold());
    }
}
