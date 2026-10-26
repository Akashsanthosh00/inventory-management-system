package com.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quantity of one product in one warehouse.
 * <p>
 * {@link #version} enables <b>optimistic locking</b>: if two transactions read the same row and both try to
 * update it, the second UPDATE ("... WHERE id=? AND version=?") affects 0 rows and Hibernate throws
 * an optimistic-lock exception instead of silently overselling.
 */
@Entity
@Table(name = "stock_levels", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "warehouse_id"}))
@Getter
@Setter
@NoArgsConstructor
public class StockLevel extends BaseEntity {

    @Column(nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(nullable = false)
    private int quantity;

    @Version
    private Long version;

    public StockLevel(Long tenantId, Product product, Warehouse warehouse, int quantity) {
        this.tenantId = tenantId;
        this.product = product;
        this.warehouse = warehouse;
        this.quantity = quantity;
    }

    public boolean isLow() {
        return quantity <= product.getLowStockThreshold();
    }
}
