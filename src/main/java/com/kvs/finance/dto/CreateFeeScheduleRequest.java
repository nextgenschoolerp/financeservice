package com.kvs.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** US-04000: the itemized fee schedule of one class for one academic year. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFeeScheduleRequest {

    /** Optional; defaults to the caller's institution (tenantId claim). A super administrator without one must send it. */
    private String tenantId;

    @NotBlank(message = "branchId is required")
    private String branchId;

    @NotBlank(message = "academicYear is required")
    @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "academicYear must look like 2026-2027")
    private String academicYear;

    /** studentservice grade id (optional) */
    private UUID gradeId;

    @NotBlank(message = "gradeName is required")
    @Size(max = 50, message = "gradeName must be at most 50 characters")
    private String gradeName;

    @NotBlank(message = "scheduleName is required")
    @Size(max = 150, message = "scheduleName must be at most 150 characters")
    private String scheduleName;

    @NotEmpty(message = "lines must contain at least one fee head installment")
    @Size(max = 60, message = "a schedule has at most 60 lines")
    @Valid
    private List<Line> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        @NotNull(message = "feeHeadId is required")
        private UUID feeHeadId;

        @NotBlank(message = "installmentLabel is required")
        @Size(max = 50, message = "installmentLabel must be at most 50 characters")
        private String installmentLabel;

        @NotNull(message = "dueDate is required")
        private LocalDate dueDate;

        @NotNull(message = "amount is required")
        @DecimalMin(value = "1.00", message = "amount must be at least 1.00")
        @Digits(integer = 8, fraction = 2, message = "amount must have at most 8 digits and 2 decimals")
        private BigDecimal amount;
    }
}
