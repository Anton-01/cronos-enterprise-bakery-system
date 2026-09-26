package com.ninsky.cronos.account.fiscal.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Value-like address columns of {@code user_fiscal_data}; codes stored as SAT/ISO strings. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FiscalAddressEmbeddable {

    @Column(name = "street", nullable = false, length = 150)
    private String street;

    @Column(name = "exterior_number", nullable = false, length = 20)
    private String exteriorNumber;

    @Column(name = "interior_number", length = 20)
    private String interiorNumber;

    @Column(name = "neighborhood", nullable = false, length = 100)
    private String neighborhood;

    @Column(name = "municipality", nullable = false, length = 100)
    private String municipality;

    @Column(name = "state", nullable = false, length = 3)
    private String state;

    @Column(name = "zip_code", nullable = false, length = 5)
    private String zipCode;

    @Column(name = "country", nullable = false, length = 3)
    private String country;
}
