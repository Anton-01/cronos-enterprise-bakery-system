package com.ninsky.cronos.application.event;

import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record NewDeviceLoginEvent(
        String email,
        String username,
        String deviceName,
        String location,
        String ipAddress,
        LocalDateTime time
) {}
