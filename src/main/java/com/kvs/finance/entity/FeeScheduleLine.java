package com.kvs.finance.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One fee head installment of a schedule, e.g. "Tuition Fee · Term 1 · due 10 Apr · ₹18,000". US-04000. */
@Entity
@Table(name = "fee_schedule_lines")
@Getter
@Setter
public class FeeScheduleLine extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "line_id")
    private UUID lineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private FeeSchedule schedule;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fee_head_id", nullable = false)
    private FeeHead feeHead;

    /** e.g. Term 1, Annual, Quarter 2 */
    @Column(name = "installment_label", nullable = false, length = 50)
    private String installmentLabel;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
}
