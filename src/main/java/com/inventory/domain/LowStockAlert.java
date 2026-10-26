package com.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "low_stock_alerts",
        indexes = @Index(name = "idx_alert_tenant_resolved", columnList = "tenant_id,resolved"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LowStockAlert extends BaseEntity {

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Long stockLevelId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 64)
    private String productSku;

    @Column(nullable = false, length = 200)
    private String productName;

    @Column(nullable = false)
    private Long warehouseId;

    @Column(nullable = false, length = 150)
    private String warehouseName;

    private int quantity;

    private int threshold;

    @Builder.Default
    @Column(nullable = false)
    private boolean resolved = false;
}
