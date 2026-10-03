package com.ninsky.cronos.account.fiscal.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** ISO 3166-2:MX subdivision codes without the "MX-" prefix. JSON carries the code ("CMX"). */
public enum MexicanState {
    AGU, BCN, BCS, CAM, CHP, CHH, CMX, COA, COL, DUR, GUA, GRO, HID, JAL, MEX, MIC,
    MOR, NAY, NLE, OAX, PUE, QUE, ROO, SLP, SIN, SON, TAB, TAM, TLA, VER, YUC, ZAC;

    private static final Map<String, MexicanState> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(MexicanState::code, Function.identity()));

    @JsonValue
    public String code() {
        return name();
    }

    public static Optional<MexicanState> fromCode(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(BY_CODE.get(code.strip().toUpperCase(Locale.ROOT)));
    }

    @JsonCreator
    public static MexicanState fromJson(String code) {
        return fromCode(code).orElse(null);
    }
}
