package com.ninsky.cronos.account.fiscal.infrastructure;

import com.ninsky.cronos.account.fiscal.api.AddressRequest;
import com.ninsky.cronos.account.fiscal.api.AddressResponse;
import com.ninsky.cronos.account.fiscal.api.FiscalDataResponse;
import com.ninsky.cronos.account.fiscal.api.UpsertFiscalDataRequest;
import com.ninsky.cronos.account.fiscal.domain.FiscalAddress;
import com.ninsky.cronos.account.fiscal.domain.FiscalData;
import com.ninsky.cronos.account.fiscal.domain.UpsertFiscalDataCommand;
import com.ninsky.cronos.account.shared.domain.ExpectedVersion;
import com.ninsky.cronos.account.shared.infrastructure.mapping.AccountMapperConfig;
import com.ninsky.cronos.account.shared.infrastructure.mapping.ValueObjectMappings;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * The fiscal aggregate's single mapper. {@code taxpayerType} is never copied from anywhere — it is
 * always derived from the RFC ({@code taxpayerTypeOf}). SAT codes and ISO state codes travel as
 * strings on the wire and in the table, as enums/value objects in the domain.
 */
@Mapper(config = AccountMapperConfig.class, uses = ValueObjectMappings.class)
public interface FiscalDataMapper {

    // -- API -> domain --

    @Mapping(target = "legalName", source = "request.legalName", qualifiedByName = "toLegalName")
    @Mapping(target = "rfc", source = "request.taxId", qualifiedByName = "toRfc")
    @Mapping(target = "taxRegime", source = "request.taxRegime", qualifiedByName = "toTaxRegime")
    @Mapping(target = "address", source = "request.address")
    @Mapping(target = "expectedVersion", source = "expectedVersion")
    UpsertFiscalDataCommand toCommand(UpsertFiscalDataRequest request, ExpectedVersion expectedVersion);

    @Mapping(target = "state", source = "state", qualifiedByName = "toMexicanState")
    @Mapping(target = "zipCode", source = "zipCode", qualifiedByName = "toZipCode")
    FiscalAddress toAddress(AddressRequest address);

    // -- domain -> API --

    @Mapping(target = "legalName", source = "legalName", qualifiedByName = "legalNameToString")
    @Mapping(target = "taxId", source = "rfc", qualifiedByName = "rfcToString")
    @Mapping(target = "taxpayerType", source = "rfc", qualifiedByName = "taxpayerTypeOf")
    @Mapping(target = "taxRegime", source = "taxRegime")
    @Mapping(target = "address", source = "address")
    @Mapping(target = "updatedAt", source = "updatedAt")
    FiscalDataResponse toResponse(FiscalData data);

    @Mapping(target = "zipCode", source = "zipCode", qualifiedByName = "zipCodeToString")
    AddressResponse toAddressResponse(FiscalAddress address);

    // -- domain <-> persistence --

    @Mapping(target = "legalName", source = "legalName", qualifiedByName = "toLegalName")
    @Mapping(target = "rfc", source = "taxId", qualifiedByName = "toRfc")
    @Mapping(target = "taxRegime", source = "taxRegime", qualifiedByName = "toTaxRegime")
    @Mapping(target = "address", source = "address")
    @Mapping(target = "version", source = "version")
    @Mapping(target = "updatedAt", source = "updatedAt")
    FiscalData toDomain(UserFiscalDataJpaEntity entity);

    @Mapping(target = "state", source = "state", qualifiedByName = "toMexicanState")
    @Mapping(target = "zipCode", source = "zipCode", qualifiedByName = "toZipCode")
    FiscalAddress fromEmbeddable(FiscalAddressEmbeddable address);

    /**
     * PUT = full replace onto a managed entity: SET_TO_NULL so a cleared optional value (e.g.
     * {@code interiorNumber}) really clears. Identity, version and audit columns are never mapped.
     */
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "legalName", source = "legalName", qualifiedByName = "legalNameToString")
    @Mapping(target = "taxId", source = "rfc", qualifiedByName = "rfcToString")
    @Mapping(target = "taxpayerType", source = "rfc", qualifiedByName = "taxpayerTypeOf")
    @Mapping(target = "taxRegime", source = "taxRegime", qualifiedByName = "taxRegimeToCode")
    @Mapping(target = "address", source = "address")
    void updateEntity(FiscalData data, @MappingTarget UserFiscalDataJpaEntity entity);

    @Mapping(target = "state", source = "state", qualifiedByName = "mexicanStateToCode")
    @Mapping(target = "zipCode", source = "zipCode", qualifiedByName = "zipCodeToString")
    FiscalAddressEmbeddable toEmbeddable(FiscalAddress address);
}
