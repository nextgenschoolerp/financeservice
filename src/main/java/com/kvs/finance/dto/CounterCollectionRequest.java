package com.kvs.finance.dto;

import com.kvs.finance.enums.PaymentMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** US-04013: a cash, cheque or demand draft payment taken at the fee counter. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterCollectionRequest {

    @NotNull(message = "studentId is required")
    private UUID studentId;

    /** Optional; defaults to the student's latest fee account */
    @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "academicYear must look like 2026-2027")
    private String academicYear;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "1.00", message = "amount must be at least 1.00")
    @Digits(integer = 8, fraction = 2, message = "amount must have at most 8 digits and 2 decimals")
    private BigDecimal amount;

    /** CASH, CHEQUE or DEMAND_DRAFT */
    @NotNull(message = "paymentMode is required")
    private PaymentMode paymentMode;

    /** Cheque / DD number (6 digits), required for CHEQUE and DEMAND_DRAFT */
    private String instrumentNumber;

    @Size(max = 100, message = "bankName must be at most 100 characters")
    private String bankName;

    private LocalDate instrumentDate;

    @Size(max = 150, message = "payerName must be at most 150 characters")
    private String payerName;

    @Size(max = 500, message = "remarks must be at most 500 characters")
    private String remarks;
}
