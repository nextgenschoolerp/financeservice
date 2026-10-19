package com.kvs.finance.dto;

import com.kvs.finance.enums.PaymentMode;
import com.kvs.finance.enums.ReceiptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A fee receipt with the ledger lines it paid. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptResponse {

    private UUID receiptId;
    private String receiptNumber;
    private UUID studentId;
    private String studentUid;
    private String studentName;
    private String gradeName;
    private String sectionName;
    private String branchId;
    private String academicYear;
    private BigDecimal totalAmountPaid;
    private PaymentMode paymentMode;
    private String instrumentNumber;
    private String bankName;
    private LocalDate instrumentDate;
    private String payerName;
    private String remarks;
    private Instant paymentTimestamp;
    private String collectedBy;
    private ReceiptStatus receiptStatus;
    private Instant bouncedAt;
    private String bouncedBy;
    private String bounceReason;
    private BigDecimal bounceCharge;
    /** Path of the PDF copy (US-04004) */
    private String pdfUrl;
    private List<Allocation> allocations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Allocation {
        private UUID ledgerId;
        private String headName;
        private String installmentLabel;
        private BigDecimal amount;
    }
}
