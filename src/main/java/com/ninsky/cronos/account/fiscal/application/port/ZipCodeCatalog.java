package com.ninsky.cronos.account.fiscal.application.port;

import com.ninsky.cronos.account.fiscal.domain.MxZipCode;

/**
 * SAT c_CodigoPostal lookup. Pluggable: the default adapter accepts every well-formed code
 * ({@code NoOpZipCodeCatalog}); a real one can be backed by the published SAT catalog.
 */
public interface ZipCodeCatalog {

    boolean isKnown(MxZipCode zipCode);
}
