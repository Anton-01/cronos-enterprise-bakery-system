package com.ninsky.cronos.domain.port.auth;

import com.ninsky.cronos.domain.model.auth.PasswordHistory;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PasswordHistoryRepositoryPort {

    PasswordHistory save(PasswordHistory passwordHistory);

    List<PasswordHistory> findByUserIdOrderByChangedAtDesc(UUID userId, Pageable pageable);
}
