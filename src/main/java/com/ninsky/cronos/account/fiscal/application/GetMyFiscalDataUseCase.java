package com.ninsky.cronos.account.fiscal.application;

import com.ninsky.cronos.account.fiscal.application.port.FiscalDataRepository;
import com.ninsky.cronos.account.fiscal.domain.FiscalData;
import com.ninsky.cronos.account.shared.application.port.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** GET /users/me/fiscal — "not registered yet" is a normal state (empty), not an error. */
@Service
@RequiredArgsConstructor
public class GetMyFiscalDataUseCase {

    private final CurrentUserProvider currentUserProvider;
    private final FiscalDataRepository fiscalDataRepository;

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Optional<FiscalData> execute() {
        return fiscalDataRepository.findByUserId(currentUserProvider.currentUserId());
    }
}
