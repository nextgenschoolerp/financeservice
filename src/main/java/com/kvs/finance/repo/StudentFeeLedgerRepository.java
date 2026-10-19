package com.kvs.finance.repo;

import com.kvs.finance.entity.StudentFeeLedger;
import com.kvs.finance.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StudentFeeLedgerRepository extends JpaRepository<StudentFeeLedger, UUID> {

    /** A student's ledger lines, oldest due first (the order payments are allocated in). */
    List<StudentFeeLedger> findByAccountAccountIdInOrderByDueDateAscInstallmentLabelAsc(Collection<UUID> accountIds);

    boolean existsByAccountAccountIdAndScheduleLineId(UUID accountId, UUID scheduleLineId);

    /** Unpaid lines of a branch (late fees, defaulter lists). */
    @Query("select l from StudentFeeLedger l where l.tenantId = :tenantId and l.account.branchId = :branchId "
            + "and l.paymentStatus in :statuses order by l.account.gradeName, l.account.sectionName, l.account.studentName, l.dueDate")
    List<StudentFeeLedger> findOpenLines(@Param("tenantId") String tenantId, @Param("branchId") String branchId,
                                         @Param("statuses") Collection<PaymentStatus> statuses);

    @Query("select l from StudentFeeLedger l where l.tenantId = :tenantId and l.account.branchId = :branchId "
            + "and l.paymentStatus <> com.kvs.finance.enums.PaymentStatus.CANCELLED")
    List<StudentFeeLedger> findBranchLines(@Param("tenantId") String tenantId, @Param("branchId") String branchId);
}
