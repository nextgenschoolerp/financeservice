package com.kvs.finance.entity;

import com.kvs.core.entity.BaseAuditEntity;
import com.kvs.finance.enums.FeeHeadCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A billing category such as Tuition, Lab or Transport (module4.sql fee_heads).
 * Covers: US-04000 (itemized fee heads) and US-04018 (mapping a head to an accounting ledger code).
 */
@Entity
@Table(name = "fee_heads",
        uniqueConstraints = @UniqueConstraint(name = "uk_fee_head_tenant_code", columnNames = {"tenant_id", "head_code"}))
@Getter
@Setter
public class FeeHead extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "fee_head_id")
    private UUID feeHeadId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    /** Short code, e.g. TUITION, LAB, TRANSPORT. */
    @Column(name = "head_code", nullable = false, length = 30)
    private String headCode;

    @Column(name = "head_name", nullable = false, length = 100)
    private String headName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private FeeHeadCategory category;

    @Column(name = "is_recurring", nullable = false)
    private boolean recurring = true;

    /** Accounting ledger the head posts to (Tally / chart of accounts), e.g. 4100-TUITION. US-04018. */
    @Column(name = "ledger_code", length = 30)
    private String ledgerCode;

    @Column(name = "ledger_name", length = 100)
    private String ledgerName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
