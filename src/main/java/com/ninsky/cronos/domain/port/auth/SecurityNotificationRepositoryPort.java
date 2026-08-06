package com.ninsky.cronos.domain.port.auth;

import com.ninsky.cronos.domain.model.auth.SecurityNotification;

public interface SecurityNotificationRepositoryPort {
    SecurityNotification save(SecurityNotification notification);
}
