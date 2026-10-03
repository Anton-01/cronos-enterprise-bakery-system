package com.ninsky.cronos.account.profile.application;

import com.ninsky.cronos.account.profile.application.port.UserAccountRepository;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.shared.application.port.CurrentUserProvider;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetMyProfileUseCase {

    private final CurrentUserProvider currentUserProvider;
    private final UserAccountRepository userAccountRepository;

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public UserAccount execute() {
        var userId = currentUserProvider.currentUserId();
        return userAccountRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
