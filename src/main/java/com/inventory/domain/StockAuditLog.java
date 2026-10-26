package com.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Append-only history of every stock movement (who, what, when, why, before/after). */
@Entity
@Table(name = "stock_audit_logs",
        indexes = @Index(name = "idx_audit_tenant_product", columnList = "tenant_id,product_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAuditLog extends BaseEntity {

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 64)
    private String productSku;

    @Column(nullable = false)
    private Long warehouseId;

    @Column(nullable = false)
    private int quantityBefore;

    @Column(nullable = false)
    private int delta;

    @Column(nullable = false)
    private int quantityAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditReason reason;

    /** Order number or free-text note. */
    @Column(length = 255)
    private String reference;

    @Column(length = 255)
    private String performedBy;
}
