package com.kvs.finance.entity;

import com.kvs.core.entity.BaseAuditEntity;
import com.kvs.finance.enums.PaymentMode;
import com.kvs.finance.enums.ReceiptStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An auto-numbered fee receipt (module4.sql fee_payment_receipts). US-04013 records counter collections,
 * US-04004 serves the receipt as a PDF and dispatches it, US-04019 marks a bounced cheque.
 */
@Entity
@Table(name = "fee_payment_receipts",
        uniqueConstraints = @UniqueConstraint(name = "uk_fee_receipt_number", columnNames = "receipt_number"),
        indexes = {
                @Index(name = "idx_fee_receipt_account", columnList = "account_id"),
                @Index(name = "idx_fee_receipt_branch_time", columnList = "tenant_id, branch_id, payment_timestamp"),
                @Index(name = "idx_payment_receipts_txnid", columnList = "gateway_transaction_id")
        })
@Getter
@Setter
public class FeePaymentReceipt extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "receipt_id")
    private UUID receiptId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "branch_id", nullable = false, length = 100)
    private String branchId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private FeeAccount account;

    /** e.g. RCPT-2026-000042 */
    @Column(name = "receipt_number", nullable = false, length = 50)
    private String receiptNumber;

    @Column(name = "total_amount_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmountPaid;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 30)
    private PaymentMode paymentMode;

    /** Cheque / demand draft number */
    @Column(name = "instrument_number", length = 20)
    private String instrumentNumber;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "instrument_date")
    private LocalDate instrumentDate;

    @Column(name = "payer_name", length = 150)
    private String payerName;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "gateway_transaction_id", length = 100)
    private String gatewayTransactionId;

    @Column(name = "payment_timestamp", nullable = false)
    private Instant paymentTimestamp;

    @Column(name = "collected_by", length = 150)
    private String collectedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_status", nullable = false, length = 20)
    private ReceiptStatus receiptStatus = ReceiptStatus.ISSUED;

    @Column(name = "bounced_at")
    private Instant bouncedAt;

    @Column(name = "bounced_by", length = 150)
    private String bouncedBy;

    @Column(name = "bounce_reason", length = 500)
    private String bounceReason;

    @Column(name = "bounce_charge", precision = 10, scale = 2)
    private BigDecimal bounceCharge;

    @Column(name = "tally_synced", nullable = false)
    private boolean tallySynced = false;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("amount DESC")
    private List<FeeReceiptAllocation> allocations = new ArrayList<>();
}
