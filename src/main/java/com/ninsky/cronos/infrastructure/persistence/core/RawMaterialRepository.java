package com.ninsky.cronos.infrastructure.persistence.core;

import com.ninsky.cronos.application.response.core.RawMaterialListResponse;
import com.ninsky.cronos.domain.entity.core.RawMaterial;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface RawMaterialRepository extends JpaRepository<RawMaterial, UUID> {

    @Query("""
        SELECT new com.ninsky.cronos.application.response.core.RawMaterialListResponse(
            r.id, r.name, c.name, u.name, r.purchaseQuantity, r.unitCost, r.yieldPercentage, r.baseUnitCost, r.status
        ) FROM RawMaterial r\s
        JOIN r.purchaseUnit u\s
        JOIN Category c on r.categoryId = c.id
        WHERE r.userId = :userId
   \s""")
    Page<RawMaterialListResponse> findAllForListByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RawMaterial r SET r.status = :status, r.updatedAt = CURRENT_TIMESTAMP WHERE r.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") RecordStatus status);
}
