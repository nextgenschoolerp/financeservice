package com.kvs.finance.entity;

import com.kvs.core.entity.BaseAuditEntity;
import com.kvs.finance.enums.DispatchChannel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A request to send a receipt to a parent (US-04004). No messaging gateway is connected yet, so rows stay QUEUED
 * until Module 09 (communicationservice) picks them up.
 */
@Entity
@Table(name = "fee_receipt_dispatches")
@Getter
@Setter
public class FeeReceiptDispatch extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "dispatch_id")
    private UUID dispatchId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private FeePaymentReceipt receipt;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private DispatchChannel channel;

    @Column(name = "recipient", nullable = false, length = 150)
    private String recipient;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "QUEUED";

    @Column(name = "requested_by", length = 150)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
}
