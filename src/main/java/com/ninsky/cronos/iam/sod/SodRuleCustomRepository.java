package com.ninsky.cronos.iam.sod;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Active SoD rules with their permission sets (seeded configuration, read-only). */
@Repository
@RequiredArgsConstructor
public class SodRuleCustomRepository {

    private final JdbcTemplate jdbc;

    @Cacheable("sodRules")
    public List<SodRule> findActive() {
        Map<String, Map<Integer, Set<String>>> sets = new TreeMap<>();
        jdbc.query("SELECT rule_code, set_index, permission_code FROM sod_rule_sets", rs -> {
            sets.computeIfAbsent(rs.getString(1), k -> new TreeMap<>())
                    .computeIfAbsent(rs.getInt(2), k -> new HashSet<>()).add(rs.getString(3));
        });
        return jdbc.query("""
                        SELECT code, name_es, name_en, description_es, description_en, severity
                        FROM sod_rules WHERE active ORDER BY code""",
                (rs, i) -> new SodRule(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        SodSeverity.valueOf(rs.getString(6)),
                        sets.getOrDefault(rs.getString(1), Map.of()).values().stream().collect(Collectors.toList())));
    }
}
