package com.kvs.finance.repo;

import com.kvs.finance.entity.FeePaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface FeePaymentReceiptRepository extends JpaRepository<FeePaymentReceipt, UUID> {

    List<FeePaymentReceipt> findByAccountAccountIdInOrderByPaymentTimestampDesc(Collection<UUID> accountIds);

    @Query("select r from FeePaymentReceipt r where r.tenantId = :tenantId and r.branchId = :branchId "
            + "and r.paymentTimestamp >= :from and r.paymentTimestamp < :to order by r.paymentTimestamp desc")
    List<FeePaymentReceipt> findBranchReceipts(@Param("tenantId") String tenantId, @Param("branchId") String branchId,
                                               @Param("from") Instant from, @Param("to") Instant to);

    /** Highest number issued with a prefix such as RCPT-2026- (numbers are zero-padded, so text order is number order). */
    @Query("select max(r.receiptNumber) from FeePaymentReceipt r where r.receiptNumber like concat(:prefix, '%')")
    String findLastNumber(@Param("prefix") String prefix);
}
