package com.ninsky.cronos.iam.access;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Entry point for "what may this user do right now". */
@Service
@RequiredArgsConstructor
public class UserAccessService {

    private final AccessVersions versions;
    private final UserAccessCache cache;

    public UserAccessState current(UUID userId) {
        return cache.load(userId, versions.current(userId));
    }
}
