package com.ninsky.cronos.account.fiscal.api.validation;

import com.ninsky.cronos.account.fiscal.api.UpsertFiscalDataRequest;
import com.ninsky.cronos.account.fiscal.domain.Rfc;
import com.ninsky.cronos.account.fiscal.domain.TaxRegime;
import com.ninsky.cronos.account.fiscal.domain.TaxpayerIdentity;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Optional;

/**
 * Cross-field: only judges applicability when both the RFC and the regime are individually valid
 * (their own constraints report otherwise), and reports on {@code taxRegime} so the frontend shows
 * the message under the regime select.
 */
public class RegimeMatchesRfcValidator implements ConstraintValidator<RegimeMatchesRfc, UpsertFiscalDataRequest> {

    @Override
    public boolean isValid(UpsertFiscalDataRequest request, ConstraintValidatorContext context) {
        if (request == null || request.taxId() == null || request.taxRegime() == null) {
            return true;
        }
        String rfc = Rfc.normalize(request.taxId());
        Optional<TaxRegime> regime = TaxRegime.fromCode(request.taxRegime());
        if (regime.isEmpty() || Rfc.check(rfc).isPresent()) {
            return true;
        }
        TaxpayerIdentity identity = new Rfc(rfc).identity();
        boolean applicable = switch (identity) {
            case TaxpayerIdentity.Individual individual -> regime.get().applicableTo(individual.type());
            case TaxpayerIdentity.LegalEntity legalEntity -> regime.get().applicableTo(legalEntity.type());
        };
        if (applicable) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("taxRegime")
                .addConstraintViolation();
        return false;
    }
}
