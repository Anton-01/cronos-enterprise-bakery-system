package com.ninsky.cronos.iam.policy;

import com.ninsky.cronos.domain.port.auth.PasswordHistoryRepositoryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Policy-driven password rules: complexity, identity fragments and the common-password list first;
 * the (expensive) history comparison only once those pass.
 */
@Component
public class DefaultPasswordPolicy implements PasswordPolicy {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%*-_+=?";
    private static final int MIN_TEMPORARY = 14;

    private final SecurityPolicyProvider policies;
    private final PasswordHistoryRepositoryPort history;
    private final PasswordEncoder encoder;
    private final CommonPasswords common = CommonPasswords.load();
    private final SecureRandom random = new SecureRandom();

    public DefaultPasswordPolicy(SecurityPolicyProvider policies, PasswordHistoryRepositoryPort history, PasswordEncoder encoder) {
        this.policies = policies;
        this.history = history;
        this.encoder = encoder;
    }

    @Override
    public List<String> violations(String password, String username, String email, UUID userId) {
        SecurityPolicy policy = policies.current();
        List<String> keys = PasswordRules.check(password, username, email, policy, common);
        if (keys.isEmpty() && userId != null && policy.passwordHistory() > 0 && reused(password, userId, policy.passwordHistory())) {
            return List.of(PasswordRules.REUSED);
        }
        return keys;
    }

    @Override
    public String generateTemporary() {
        int length = Math.min(Math.max(policies.current().passwordMinLength(), MIN_TEMPORARY), PasswordRules.MAX_UTF8_BYTES);
        String all = UPPER + LOWER + DIGITS + SYMBOLS;
        List<Character> chars = new ArrayList<>(Stream.of(UPPER, LOWER, DIGITS, SYMBOLS).map(this::pick).toList());
        IntStream.range(chars.size(), length).forEach(i -> chars.add(pick(all)));
        Collections.shuffle(chars, random);
        String candidate = chars.stream().map(String::valueOf).reduce("", String::concat);
        return common.matches(candidate) ? generateTemporary() : candidate;
    }

    private boolean reused(String password, UUID userId, int depth) {
        return history.findByUserIdOrderByChangedAtDesc(userId, PageRequest.of(0, depth)).stream()
                .anyMatch(entry -> encoder.matches(password, entry.getPasswordHash()));
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
