package com.kvs.finance.repo;

import com.kvs.finance.entity.FeeReceiptDispatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeeReceiptDispatchRepository extends JpaRepository<FeeReceiptDispatch, UUID> {

    List<FeeReceiptDispatch> findByReceiptReceiptIdOrderByRequestedAtDesc(UUID receiptId);
}
