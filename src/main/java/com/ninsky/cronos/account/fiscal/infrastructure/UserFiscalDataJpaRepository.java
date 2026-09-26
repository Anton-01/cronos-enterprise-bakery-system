package com.ninsky.cronos.account.fiscal.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserFiscalDataJpaRepository extends JpaRepository<UserFiscalDataJpaEntity, UUID> {
}
