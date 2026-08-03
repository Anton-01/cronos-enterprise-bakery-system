package com.ninsky.cronos.domain.entity.quote;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import com.ninsky.cronos.domain.entity.recipes.Recipe;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "quote_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuoteItem extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    // Cambiamos de UUID puro a @ManyToOne para poder extraer la foto al momento de cotizar.
    // Lo dejamos nullable = true por si el cliente pide un pastel 100% personalizado que no está en tu catálogo.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @Column(name = "recipe_version_id")
    private UUID recipeVersionId;

    // ==========================================
    // 1. PATRÓN SNAPSHOT: DATOS CONGELADOS (VISTA)
    // ==========================================

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "product_description", length = 1000)
    private String productDescription;

    @Column(name = "product_size", length = 100)
    private String productSize;

    @Column(name = "image_file_path", length = 500)
    private String imageFilePath; // Ruta en GCP para que la foto no cambie si la receta cambia

    // ==========================================
    // 2. LÓGICA FINANCIERA (RENTABILIDAD)
    // ==========================================

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "scale_factor", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal scaleFactor = BigDecimal.ONE;

    @Column(name = "profit_margin_id")
    private UUID profitMarginId;

    // Con esto sabes cuánto te costó hacer este pastel el 8 de Abril de 2026
    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "profit_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal profitPercentage;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    // ==========================================
    // 3. METADATOS Y UX
    // ==========================================

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Version
    @Builder.Default
    private Long version = 0L;
}