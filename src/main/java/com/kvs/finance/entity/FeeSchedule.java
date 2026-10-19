package com.kvs.finance.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The itemized fee schedule of one class for one academic year: tuition, lab, transport … heads split into
 * installments with due dates. US-04000.
 */
@Entity
@Table(name = "fee_schedules",
        uniqueConstraints = @UniqueConstraint(name = "uk_fee_schedule_class_year",
                columnNames = {"tenant_id", "branch_id", "academic_year", "grade_name"}))
@Getter
@Setter
public class FeeSchedule extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "branch_id", nullable = false, length = 100)
    private String branchId;

    /** e.g. 2026-2027 */
    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    /** studentservice grade id, when known */
    @Column(name = "grade_id")
    private UUID gradeId;

    /** e.g. Class 6 */
    @Column(name = "grade_name", nullable = false, length = 50)
    private String gradeName;

    @Column(name = "schedule_name", nullable = false, length = 150)
    private String scheduleName;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dueDate ASC, installmentLabel ASC")
    private List<FeeScheduleLine> lines = new ArrayList<>();
}
