package com.ninsky.cronos.account.fiscal.application;

import com.ninsky.cronos.account.fiscal.domain.UpsertFiscalDataCommand;
import com.ninsky.cronos.account.shared.domain.AccountDomainError;

import java.util.List;

/**
 * One business rule over a fully-typed fiscal upsert. Rules are Spring beans collected as an
 * {@code @Order}-sorted {@code List<FiscalRule>}: adding a rule is adding a bean, never editing
 * the use case or another rule. Return every violation found (empty = pass), never throw.
 */
@FunctionalInterface
public interface FiscalRule {

    List<AccountDomainError> check(UpsertFiscalDataCommand command);
}
