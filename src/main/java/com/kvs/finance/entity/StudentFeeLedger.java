package com.kvs.finance.entity;

import com.kvs.core.entity.BaseAuditEntity;
import com.kvs.finance.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One fee demand (a fee head installment) posted to a student (module4.sql student_fee_ledgers).
 * Net payable = base − concession + late fine; outstanding = net payable − paid.
 */
@Entity
@Table(name = "student_fee_ledgers",
        uniqueConstraints = @UniqueConstraint(name = "uk_fee_ledger_account_line", columnNames = {"account_id", "schedule_line_id"}),
        indexes = @Index(name = "idx_fee_ledgers_account_status", columnList = "account_id, payment_status"))
@Getter
@Setter
public class StudentFeeLedger extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ledger_id")
    private UUID ledgerId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private FeeAccount account;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Column(name = "schedule_line_id", nullable = false)
    private UUID scheduleLineId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fee_head_id", nullable = false)
    private FeeHead feeHead;

    @Column(name = "installment_label", nullable = false, length = 50)
    private String installmentLabel;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "base_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "concession_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal concessionAmount = BigDecimal.ZERO;

    @Column(name = "late_fine_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal lateFineAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    public BigDecimal netPayable() {
        return baseAmount.subtract(concessionAmount).add(lateFineAmount);
    }

    public BigDecimal outstanding() {
        return netPayable().subtract(paidAmount).max(BigDecimal.ZERO);
    }

    /** Recomputes the status from the amounts and the due date (OVERDUE once the due date has passed). */
    public void refreshStatus(LocalDate today) {
        if (paymentStatus == PaymentStatus.CANCELLED) {
            return;
        }
        if (outstanding().signum() == 0) {
            paymentStatus = PaymentStatus.PAID;
        } else if (dueDate.isBefore(today)) {
            paymentStatus = PaymentStatus.OVERDUE;
        } else if (paidAmount.signum() > 0) {
            paymentStatus = PaymentStatus.PARTIALLY_PAID;
        } else {
            paymentStatus = PaymentStatus.PENDING;
        }
    }
}
