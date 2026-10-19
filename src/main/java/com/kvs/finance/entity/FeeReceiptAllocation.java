package com.kvs.finance.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** The part of a receipt credited to one ledger line; reversed when a cheque bounces (US-04019). */
@Entity
@Table(name = "fee_receipt_allocations")
@Getter
@Setter
public class FeeReceiptAllocation extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "allocation_id")
    private UUID allocationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private FeePaymentReceipt receipt;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ledger_id", nullable = false)
    private StudentFeeLedger ledger;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
}
