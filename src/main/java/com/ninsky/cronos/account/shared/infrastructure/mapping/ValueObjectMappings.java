package com.ninsky.cronos.account.shared.infrastructure.mapping;

import com.ninsky.cronos.account.fiscal.domain.LegalName;
import com.ninsky.cronos.account.fiscal.domain.MexicanState;
import com.ninsky.cronos.account.fiscal.domain.MxZipCode;
import com.ninsky.cronos.account.fiscal.domain.Rfc;
import com.ninsky.cronos.account.fiscal.domain.TaxRegime;
import com.ninsky.cronos.account.fiscal.domain.TaxpayerType;
import com.ninsky.cronos.account.profile.domain.E164Phone;
import com.ninsky.cronos.account.shared.domain.DomainValidationException;
import org.mapstruct.Named;

/**
 * {@code @Named} value-object ↔ primitive conversions shared by the account mappers. Static, so
 * MapStruct calls them directly (nothing to inject). "to*" directions self-validate through the
 * value object's constructor.
 */
public final class ValueObjectMappings {

    private ValueObjectMappings() {
    }

    @Named("toRfc")
    public static Rfc toRfc(String value) {
        return value == null ? null : new Rfc(value);
    }

    @Named("rfcToString")
    public static String rfcToString(Rfc rfc) {
        return rfc == null ? null : rfc.value();
    }

    @Named("taxpayerTypeOf")
    public static TaxpayerType taxpayerTypeOf(Rfc rfc) {
        return rfc == null ? null : rfc.taxpayerType();
    }

    @Named("toLegalName")
    public static LegalName toLegalName(String value) {
        return value == null ? null : new LegalName(value);
    }

    @Named("legalNameToString")
    public static String legalNameToString(LegalName legalName) {
        return legalName == null ? null : legalName.value();
    }

    @Named("toTaxRegime")
    public static TaxRegime toTaxRegime(String code) {
        if (code == null) {
            return null;
        }
        return TaxRegime.fromCode(code).orElseThrow(() -> new DomainValidationException("account.fiscal.taxRegime.invalid"));
    }

    @Named("taxRegimeToCode")
    public static String taxRegimeToCode(TaxRegime regime) {
        return regime == null ? null : regime.code();
    }

    @Named("toMexicanState")
    public static MexicanState toMexicanState(String code) {
        if (code == null) {
            return null;
        }
        return MexicanState.fromCode(code).orElseThrow(() -> new DomainValidationException("account.fiscal.state.invalid"));
    }

    @Named("mexicanStateToCode")
    public static String mexicanStateToCode(MexicanState state) {
        return state == null ? null : state.code();
    }

    @Named("toZipCode")
    public static MxZipCode toZipCode(String value) {
        return value == null ? null : new MxZipCode(value);
    }

    @Named("zipCodeToString")
    public static String zipCodeToString(MxZipCode zipCode) {
        return zipCode == null ? null : zipCode.value();
    }

    @Named("toE164")
    public static E164Phone toE164(String value) {
        return E164Phone.ofNullable(value);
    }

    @Named("e164ToString")
    public static String e164ToString(E164Phone phone) {
        return phone == null ? null : phone.value();
    }
}
