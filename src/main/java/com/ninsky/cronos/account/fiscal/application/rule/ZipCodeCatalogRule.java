package com.ninsky.cronos.account.fiscal.application.rule;

import com.ninsky.cronos.account.fiscal.application.FiscalRule;
import com.ninsky.cronos.account.fiscal.application.port.ZipCodeCatalog;
import com.ninsky.cronos.account.fiscal.domain.UpsertFiscalDataCommand;
import com.ninsky.cronos.account.shared.domain.AccountDomainError;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/** Well-formed is not enough when a SAT catalog is plugged in: the code must actually exist. */
@Component
@Order(20)
@RequiredArgsConstructor
public class ZipCodeCatalogRule implements FiscalRule {

    private final ZipCodeCatalog zipCodeCatalog;

    @Override
    public List<AccountDomainError> check(UpsertFiscalDataCommand command) {
        return zipCodeCatalog.isKnown(command.address().zipCode())
                ? List.of()
                : List.of(AccountDomainError.InvalidField.of("address.zipCode", "account.fiscal.zipCode.unknown"));
    }
}
