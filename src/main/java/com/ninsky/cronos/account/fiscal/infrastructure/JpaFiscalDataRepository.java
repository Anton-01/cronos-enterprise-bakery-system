package com.ninsky.cronos.account.fiscal.infrastructure;

import com.ninsky.cronos.account.fiscal.application.port.FiscalDataRepository;
import com.ninsky.cronos.account.fiscal.domain.FiscalData;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JpaFiscalDataRepository implements FiscalDataRepository {

    private final UserFiscalDataJpaRepository jpaRepository;
    private final FiscalDataMapper mapper;

    @Override
    public Optional<FiscalData> findByUserId(UUID userId) {
        return jpaRepository.findById(userId).map(mapper::toDomain);
    }

    /**
     * Mutates the managed row (or a new one) and flushes, so the returned state carries the new
     * version and {@code updatedAt}. A version that moved since the caller read it is a lost update.
     */
    @Override
    public FiscalData save(FiscalData data) {
        UserFiscalDataJpaEntity entity = jpaRepository.findById(data.userId())
                .orElseGet(() -> new UserFiscalDataJpaEntity(data.userId()));
        if (!Objects.equals(entity.getVersion(), data.version())) {
            throw new ObjectOptimisticLockingFailureException(UserFiscalDataJpaEntity.class, data.userId());
        }
        mapper.updateEntity(data, entity);
        return mapper.toDomain(jpaRepository.saveAndFlush(entity));
    }
}
