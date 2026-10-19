package com.kvs.finance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** US-04005: accrue daily late fees on dues past the grace period. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccrueLateFeesRequest {

    /** Optional; defaults to the caller's institution (tenantId claim). A super administrator without one must send it. */
    private String tenantId;

    @NotBlank(message = "branchId is required")
    private String branchId;

    /** Optional; defaults to today. Must not be in the future. */
    private LocalDate asOfDate;

    @NotNull(message = "graceDays is required")
    @Min(value = 0, message = "graceDays must be 0 or more")
    @Max(value = 60, message = "graceDays must be at most 60")
    private Integer graceDays;

    @NotNull(message = "finePerDay is required")
    @DecimalMin(value = "1.00", message = "finePerDay must be at least 1.00")
    @DecimalMax(value = "1000.00", message = "finePerDay must be at most 1000.00")
    private BigDecimal finePerDay;

    @NotNull(message = "maxFinePerInstallment is required")
    @DecimalMin(value = "0.00", message = "maxFinePerInstallment must not be negative")
    @Digits(integer = 6, fraction = 2, message = "maxFinePerInstallment must have at most 6 digits and 2 decimals")
    private BigDecimal maxFinePerInstallment;
}
