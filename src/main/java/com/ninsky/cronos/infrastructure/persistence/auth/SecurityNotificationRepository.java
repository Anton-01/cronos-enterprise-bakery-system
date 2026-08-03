package com.ninsky.cronos.infrastructure.persistence.auth;

import com.ninsky.cronos.domain.entity.auth.SecurityNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface SecurityNotificationRepository extends JpaRepository<SecurityNotification, UUID> { }
