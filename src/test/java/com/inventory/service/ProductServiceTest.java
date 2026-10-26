package com.inventory.service;

import com.inventory.domain.Product;
import com.inventory.dto.ProductRequest;
import com.inventory.dto.ProductResponse;
import com.inventory.exception.ConflictException;
import com.inventory.exception.NotFoundException;
import com.inventory.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository repository;
    @InjectMocks ProductService service;

    private final ProductRequest request =
            new ProductRequest(" RICE-5KG ", "Basmati Rice 5kg", "premium", new BigDecimal("499.00"), null);

    @Test
    void createTrimsSkuAndAppliesDefaultThreshold() {
        when(repository.existsByTenantIdAndSkuIgnoreCase(1L, "RICE-5KG")).thenReturn(false);
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = service.create(1L, request);

        assertThat(response.sku()).isEqualTo("RICE-5KG");
        assertThat(response.lowStockThreshold()).isEqualTo(10);
        assertThat(response.active()).isTrue();
    }

    @Test
    void createRejectsDuplicateSkuWithinSameTenant() {
        when(repository.existsByTenantIdAndSkuIgnoreCase(1L, "RICE-5KG")).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, request)).isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void getByIdIsScopedToTenant() {
        when(repository.findByIdAndTenantId(5L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(2L, 5L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void searchBuildsLowerCasedLikePattern() {
        when(repository.search(eq(1L), eq("%widget%"), any())).thenReturn(Page.empty());

        var result = service.search(1L, "  WiDgEt ", PageRequest.of(0, 10));

        assertThat(result.content()).isEmpty();
        verify(repository).search(eq(1L), eq("%widget%"), any());
    }

    @Test
    void deactivateIsASoftDelete() {
        Product p = Product.builder().tenantId(1L).sku("A").name("A").price(BigDecimal.ONE).build();
        when(repository.findByIdAndTenantId(9L, 1L)).thenReturn(Optional.of(p));

        service.deactivate(1L, 9L);

        assertThat(p.isActive()).isFalse();
        verify(repository, never()).delete(any());
    }
}
