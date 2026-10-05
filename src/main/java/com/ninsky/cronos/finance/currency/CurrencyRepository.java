package com.ninsky.cronos.finance.currency;

import com.ninsky.cronos.finance.shared.FinanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Write-side access to {@code currencies}. */
public interface CurrencyRepository extends JpaRepository<CurrencyEntity, Long> {

    Optional<CurrencyEntity> findByIsDefaultTrue();

    Optional<CurrencyEntity> findByCode(String code);

    List<CurrencyEntity> findByStatusOrderByIsDefaultDescCodeAsc(FinanceStatus status);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByNumericCodeAndIdNot(String numericCode, Long id);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
