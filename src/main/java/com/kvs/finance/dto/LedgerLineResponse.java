package com.kvs.finance.dto;

import com.kvs.finance.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One fee demand of a student with its amounts and status. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerLineResponse {

    private UUID ledgerId;
    private UUID feeHeadId;
    private String headCode;
    private String headName;
    private String installmentLabel;
    private LocalDate dueDate;
    private BigDecimal baseAmount;
    private BigDecimal concessionAmount;
    private BigDecimal lateFineAmount;
    private BigDecimal netPayable;
    private BigDecimal paidAmount;
    private BigDecimal outstanding;
    private PaymentStatus paymentStatus;
}
