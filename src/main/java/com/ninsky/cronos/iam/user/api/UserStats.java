package com.ninsky.cronos.iam.user.api;

import com.ninsky.cronos.iam.user.UserStatus;

import java.util.Map;

/** Dashboard counters; every status key is present. */
public record UserStats(long total, Map<UserStatus, Long> byStatus, long twoFactorEnabled, long neverLoggedIn,
                        long dormant, long expiringSoon) {
}
