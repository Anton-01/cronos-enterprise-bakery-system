package com.ninsky.cronos.finance.settings;

import org.springframework.data.jpa.repository.JpaRepository;

/** Access to the singleton {@code finance_settings} row. */
public interface FinanceSettingsRepository extends JpaRepository<FinanceSettingsEntity, Short> {
}
