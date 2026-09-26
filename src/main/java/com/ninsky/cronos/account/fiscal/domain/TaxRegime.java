package com.ninsky.cronos.account.fiscal.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SAT catalog c_RegimenFiscal (CFDI 4.0). JSON carries the 3-digit SAT key ("626"), never the
 * constant name. Applicability per taxpayer type is a single immutable {@link EnumMap} built once.
 */
public enum TaxRegime {
    GENERAL_LEY_PERSONAS_MORALES("601"),
    PERSONAS_MORALES_FINES_NO_LUCRATIVOS("603"),
    SUELDOS_Y_SALARIOS("605"),
    ARRENDAMIENTO("606"),
    ENAJENACION_ADQUISICION_BIENES("607"),
    DEMAS_INGRESOS("608"),
    RESIDENTES_EXTRANJERO("610"),
    DIVIDENDOS("611"),
    ACTIVIDADES_EMPRESARIALES_PROFESIONALES("612"),
    INTERESES("614"),
    OBTENCION_PREMIOS("615"),
    SIN_OBLIGACIONES_FISCALES("616"),
    SOCIEDADES_COOPERATIVAS_PRODUCCION("620"),
    INCORPORACION_FISCAL("621"),
    ACTIVIDADES_AGRICOLAS_GANADERAS("622"),
    OPCIONAL_GRUPOS_SOCIEDADES("623"),
    COORDINADOS("624"),
    PLATAFORMAS_TECNOLOGICAS("625"),
    REGIMEN_SIMPLIFICADO_CONFIANZA("626");

    private static final Map<String, TaxRegime> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(TaxRegime::code, Function.identity()));

    private static final Map<TaxRegime, Set<TaxpayerType>> APPLICABILITY = buildApplicability();

    private final String code;

    TaxRegime(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public static Optional<TaxRegime> fromCode(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(BY_CODE.get(code.strip()));
    }

    /** Jackson entry point: unknown codes deserialize to null so Bean Validation reports them per field. */
    @JsonCreator
    public static TaxRegime fromJson(String code) {
        return fromCode(code).orElse(null);
    }

    public boolean applicableTo(TaxpayerType type) {
        return APPLICABILITY.get(this).contains(type);
    }

    public boolean applicableTo(TaxpayerIdentity identity) {
        return applicableTo(identity.type());
    }

    public Set<TaxpayerType> applicableTypes() {
        return APPLICABILITY.get(this);
    }

    private static Map<TaxRegime, Set<TaxpayerType>> buildApplicability() {
        EnumSet<TaxpayerType> individualOnly = EnumSet.of(TaxpayerType.INDIVIDUAL);
        EnumSet<TaxpayerType> legalEntityOnly = EnumSet.of(TaxpayerType.LEGAL_ENTITY);
        EnumSet<TaxpayerType> both = EnumSet.allOf(TaxpayerType.class);

        EnumMap<TaxRegime, Set<TaxpayerType>> matrix = new EnumMap<>(TaxRegime.class);
        for (TaxRegime regime : EnumSet.of(SUELDOS_Y_SALARIOS, ARRENDAMIENTO, ENAJENACION_ADQUISICION_BIENES,
                DEMAS_INGRESOS, DIVIDENDOS, ACTIVIDADES_EMPRESARIALES_PROFESIONALES, INTERESES, OBTENCION_PREMIOS,
                SIN_OBLIGACIONES_FISCALES, INCORPORACION_FISCAL, PLATAFORMAS_TECNOLOGICAS)) {
            matrix.put(regime, Collections.unmodifiableSet(individualOnly));
        }
        for (TaxRegime regime : EnumSet.of(GENERAL_LEY_PERSONAS_MORALES, PERSONAS_MORALES_FINES_NO_LUCRATIVOS,
                SOCIEDADES_COOPERATIVAS_PRODUCCION, ACTIVIDADES_AGRICOLAS_GANADERAS, OPCIONAL_GRUPOS_SOCIEDADES, COORDINADOS)) {
            matrix.put(regime, Collections.unmodifiableSet(legalEntityOnly));
        }
        for (TaxRegime regime : EnumSet.of(RESIDENTES_EXTRANJERO, REGIMEN_SIMPLIFICADO_CONFIANZA)) {
            matrix.put(regime, Collections.unmodifiableSet(both));
        }
        if (matrix.size() != values().length) {
            throw new IllegalStateException("Every TaxRegime must appear in the applicability matrix");
        }
        return Collections.unmodifiableMap(matrix);
    }
}
