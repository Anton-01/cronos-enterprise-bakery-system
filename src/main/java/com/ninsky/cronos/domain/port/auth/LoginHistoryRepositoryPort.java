package com.ninsky.cronos.domain.port.auth;

import com.ninsky.cronos.domain.model.auth.LoginHistory;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface LoginHistoryRepositoryPort {
    LoginHistory save(LoginHistory loginHistory);
    List<LoginHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
