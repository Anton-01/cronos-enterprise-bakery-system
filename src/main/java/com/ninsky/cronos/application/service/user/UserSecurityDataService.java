package com.ninsky.cronos.application.service.user;

import com.ninsky.cronos.application.response.auth.LoginHistoryResponse;
import com.ninsky.cronos.application.response.auth.UserSessionResponse;
import com.ninsky.cronos.domain.entity.auth.LoginHistory;
import com.ninsky.cronos.domain.entity.auth.User;
import com.ninsky.cronos.domain.entity.auth.UserSession;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import com.ninsky.cronos.infrastructure.persistence.auth.LoginHistoryRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.UserRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSecurityDataService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final LoginHistoryRepository loginHistoryRepository;

    @Transactional(readOnly = true)
    public List<UserSessionResponse> getActiveSessionsByUsername(String username, String currentIp, String currentUserAgent) {
        log.info("Fetching active sessions for user: {}", username);

        User user = userRepository.findByUsername(username).orElseThrow(() -> {
            log.error("Failed to fetch active sessions. User not found: {}", username);
            return new UserNotFoundException("User not found");
        });

        List<UserSession> activeSessions = userSessionRepository
                .findByUserIdAndIsActiveTrueOrderByLastActivityAtDesc(user.getId());

        log.debug("Found {} active sessions for user ID: {}", activeSessions.size(), user.getId());

        return activeSessions.stream().map(session -> {
            boolean isCurrent = session.getIpAddress().equals(currentIp) && session.getUserAgent().equals(currentUserAgent);

            return UserSessionResponse.builder().id(session.getId()).ipAddress(session.getIpAddress())
                    .userAgent(session.getUserAgent()).lastActivityAt(session.getLastActivityAt()).isActive(session.isActive())
                    .isCurrentSession(isCurrent).browser(session.getBrowser()).os(session.getOperatingSystem())
                    .device(session.getDevice()).location(session.getLocation()).build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<LoginHistoryResponse> getLoginHistoryByUsername(String username) {
        log.info("Retrieving login history for user: {}", username);

        User user = userRepository.findByUsername(username).orElseThrow(() -> {
            log.error("Failed to retrieve login history. User not found: {}", username);
            return new UserNotFoundException("User not found");
        });

        List<LoginHistory> loginHistory = loginHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), org.springframework.data.domain.PageRequest.of(0, 50));

        log.debug("Retrieved {} login history records for user ID: {}", loginHistory.size(), user.getId());

        return loginHistory.stream().map(history -> LoginHistoryResponse.builder()
                .ipAddress(history.getIpAddress()).userAgent(history.getUserAgent()).status(history.getStatus())
                .failureReason(history.getFailureReason()).createdAt(history.getCreatedAt())
                .build()).toList();
    }
}
