package com.kvs.finance.repo;

import com.kvs.finance.entity.FeeSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeeScheduleRepository extends JpaRepository<FeeSchedule, UUID> {

    boolean existsByTenantIdAndBranchIdAndAcademicYearAndGradeNameIgnoreCase(String tenantId, String branchId,
                                                                            String academicYear, String gradeName);

    List<FeeSchedule> findByTenantIdAndBranchIdOrderByAcademicYearDescGradeNameAsc(String tenantId, String branchId);
}
