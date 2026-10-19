package com.kvs.finance.repo;

import com.kvs.finance.entity.FeeHead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeeHeadRepository extends JpaRepository<FeeHead, UUID> {

    boolean existsByTenantIdAndHeadCode(String tenantId, String headCode);

    List<FeeHead> findByTenantIdOrderByHeadNameAsc(String tenantId);
}
