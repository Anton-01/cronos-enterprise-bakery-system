package com.ninsky.cronos.finance.taxrate;

import com.ninsky.cronos.finance.shared.FinanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Write-side access to {@code tax_rates}. */
public interface TaxRateRepository extends JpaRepository<TaxRateEntity, Long> {

    Optional<TaxRateEntity> findByIsDefaultTrue();

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** ACTIVE and valid on {@code day}, default first (spec §10.3). */
    @Query("""
            SELECT t FROM TaxRateEntity t
            WHERE t.status = :status AND t.validFrom <= :day AND (t.validTo IS NULL OR t.validTo >= :day)
            ORDER BY t.isDefault DESC, t.ratePercent DESC NULLS LAST, t.code ASC""")
    List<TaxRateEntity> findSelectable(@Param("status") FinanceStatus status, @Param("day") LocalDate day);
}
