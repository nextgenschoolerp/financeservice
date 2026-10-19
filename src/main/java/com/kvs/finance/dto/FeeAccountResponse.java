package com.kvs.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A student's fee status: totals, the next due installment, ledger lines and receipts. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeAccountResponse {

    private UUID accountId;
    private UUID studentId;
    private String studentUid;
    private String studentName;
    private String gradeName;
    private String sectionName;
    private String branchId;
    private String academicYear;
    /** PAID (nothing outstanding), DUE (outstanding, nothing overdue) or OVERDUE */
    private String feeStatus;
    private BigDecimal totalDemanded;
    private BigDecimal totalConcession;
    private BigDecimal totalLateFine;
    private BigDecimal totalPaid;
    private BigDecimal outstanding;
    private BigDecimal overdueAmount;
    private LocalDate nextDueDate;
    private BigDecimal nextDueAmount;
    private List<LedgerLineResponse> ledger;
    private List<ReceiptResponse> receipts;
}
