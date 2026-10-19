package com.kvs.finance.repo;

import com.kvs.finance.entity.FeeAccount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeeAccountRepository extends JpaRepository<FeeAccount, UUID> {

    Optional<FeeAccount> findByTenantIdAndStudentIdAndAcademicYear(String tenantId, UUID studentId, String academicYear);

    List<FeeAccount> findByStudentIdOrderByAcademicYearDesc(UUID studentId);

    /** Fee counter search by name or UID (blank text lists the branch). */
    @Query("select a from FeeAccount a where a.tenantId = :tenantId and a.branchId = :branchId and a.academicYear = :year "
            + "and (:text = '' or lower(a.studentName) like concat('%', :text, '%') or lower(a.studentUid) like concat('%', :text, '%')) "
            + "order by a.studentName")
    List<FeeAccount> search(@Param("tenantId") String tenantId, @Param("branchId") String branchId, @Param("year") String academicYear,
                            @Param("text") String text, Pageable page);

    @Query("select max(a.academicYear) from FeeAccount a where a.tenantId = :tenantId and a.branchId = :branchId")
    String latestYear(@Param("tenantId") String tenantId, @Param("branchId") String branchId);
}
