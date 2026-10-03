package com.ninsky.cronos.account.fiscal.application.rule;

import com.ninsky.cronos.account.fiscal.application.FiscalRule;
import com.ninsky.cronos.account.fiscal.domain.TaxpayerIdentity;
import com.ninsky.cronos.account.fiscal.domain.UpsertFiscalDataCommand;
import com.ninsky.cronos.account.shared.domain.AccountDomainError;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The regime must be available to the taxpayer kind the RFC encodes. Also enforced at the API edge
 * by {@code @RegimeMatchesRfc}; kept here so the invariant holds for any caller of the use case.
 */
@Component
@Order(10)
public class RegimeApplicabilityRule implements FiscalRule {

    @Override
    public List<AccountDomainError> check(UpsertFiscalDataCommand command) {
        TaxpayerIdentity identity = command.identity();
        boolean applicable = switch (identity) {
            case TaxpayerIdentity.Individual individual -> command.taxRegime().applicableTo(individual.type());
            case TaxpayerIdentity.LegalEntity legalEntity -> command.taxRegime().applicableTo(legalEntity.type());
        };
        return applicable
                ? List.of()
                : List.of(AccountDomainError.RegimeNotApplicable.of(command.taxRegime().code(), identity.type().name()));
    }
}
