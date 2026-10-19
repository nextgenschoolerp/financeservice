package com.kvs.finance.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A student's fee account for one academic year. It keeps a copy of the student's identity and class from
 * studentservice (Module 03) as it was when the fees were posted, so ledgers, receipts and defaulter lists do not
 * depend on another service being up.
 */
@Entity
@Table(name = "fee_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_fee_account_student_year",
                columnNames = {"tenant_id", "student_id", "academic_year"}),
        indexes = @Index(name = "idx_fee_account_branch_grade", columnList = "tenant_id, branch_id, grade_name"))
@Getter
@Setter
public class FeeAccount extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "branch_id", nullable = false, length = 100)
    private String branchId;

    /** studentservice student_master.student_id */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "student_uid", nullable = false, length = 50)
    private String studentUid;

    @Column(name = "student_name", nullable = false, length = 200)
    private String studentName;

    @Column(name = "grade_name", nullable = false, length = 50)
    private String gradeName;

    @Column(name = "section_name", length = 10)
    private String sectionName;

    /** authservice user id of the parent account that may see this student's fees */
    @Column(name = "parent_account_id", length = 100)
    private String parentAccountId;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;
}
