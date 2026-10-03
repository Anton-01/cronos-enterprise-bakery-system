package com.ninsky.cronos.account.fiscal.infrastructure;

import com.ninsky.cronos.account.fiscal.api.AddressRequest;
import com.ninsky.cronos.account.fiscal.api.FiscalDataResponse;
import com.ninsky.cronos.account.fiscal.api.UpsertFiscalDataRequest;
import com.ninsky.cronos.account.fiscal.domain.FiscalData;
import com.ninsky.cronos.account.fiscal.domain.MexicanState;
import com.ninsky.cronos.account.fiscal.domain.TaxRegime;
import com.ninsky.cronos.account.fiscal.domain.TaxpayerType;
import com.ninsky.cronos.account.fiscal.domain.UpsertFiscalDataCommand;
import com.ninsky.cronos.account.shared.domain.ExpectedVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(FiscalDataMapperImpl.class)
class FiscalDataMapperTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private FiscalDataMapper mapper;

    @Test
    void requestToCommandBuildsValueObjects() {
        UpsertFiscalDataCommand command = mapper.toCommand(request("4B"), ExpectedVersion.of(2));

        assertThat(command.legalName().value()).isEqualTo("PASTELERIA CRONOS");
        assertThat(command.rfc().value()).isEqualTo("GODE561231GR8");
        assertThat(command.taxRegime()).isEqualTo(TaxRegime.REGIMEN_SIMPLIFICADO_CONFIANZA);
        assertThat(command.address().state()).isEqualTo(MexicanState.CMX);
        assertThat(command.address().zipCode().value()).isEqualTo("06600");
        assertThat(command.expectedVersion()).isEqualTo(ExpectedVersion.of(2));
    }

    @Test
    void responseDerivesTaxpayerTypeFromTheRfcAndKeepsNulls() {
        FiscalData data = mapper.toCommand(request(null), ExpectedVersion.ANY).toFiscalData(USER_ID, 1L, LocalDateTime.of(2026, 9, 26, 18, 0));

        FiscalDataResponse response = mapper.toResponse(data);

        assertThat(response.taxpayerType()).isEqualTo(TaxpayerType.INDIVIDUAL);
        assertThat(response.taxId()).isEqualTo("GODE561231GR8");
        assertThat(response.taxRegime()).isEqualTo(TaxRegime.REGIMEN_SIMPLIFICADO_CONFIANZA);
        assertThat(response.address().interiorNumber()).isNull();
        assertThat(response.address().zipCode()).isEqualTo("06600");
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 26, 18, 0));
    }

    @Test
    void domainEntityRoundTrip() {
        FiscalData data = mapper.toCommand(request("4B"), ExpectedVersion.ANY).toFiscalData(USER_ID, null, null);
        UserFiscalDataJpaEntity entity = new UserFiscalDataJpaEntity(USER_ID);

        mapper.updateEntity(data, entity);

        assertThat(entity.getTaxpayerType()).isEqualTo(TaxpayerType.INDIVIDUAL);
        assertThat(entity.getTaxRegime()).isEqualTo("626");
        assertThat(entity.getAddress().getState()).isEqualTo("CMX");
        assertThat(mapper.toDomain(entity)).usingRecursiveComparison().isEqualTo(data);
    }

    @Test
    void updateIsAFullReplaceNullClears() {
        UserFiscalDataJpaEntity entity = new UserFiscalDataJpaEntity(USER_ID);
        mapper.updateEntity(mapper.toCommand(request("4B"), ExpectedVersion.ANY).toFiscalData(USER_ID, null, null), entity);
        entity.setVersion(7L);

        mapper.updateEntity(mapper.toCommand(request(null), ExpectedVersion.ANY).toFiscalData(USER_ID, 7L, null), entity);

        assertThat(entity.getAddress().getInteriorNumber()).isNull();
        assertThat(entity.getVersion()).as("version is never mapped").isEqualTo(7L);
        assertThat(entity.getUserId()).isEqualTo(USER_ID);
    }

    private static UpsertFiscalDataRequest request(String interiorNumber) {
        return new UpsertFiscalDataRequest("Pasteleria Cronos", "gode561231gr8", "626",
                new AddressRequest("Av. Reforma", "222", interiorNumber, "Juárez", "Cuauhtémoc", "cmx", "06600", "MEX"));
    }
}
