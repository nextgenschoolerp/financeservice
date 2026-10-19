package com.kvs.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Result of a late fee run. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LateFeeAccrualResponse {

    private String branchId;
    private LocalDate asOfDate;
    private int graceDays;
    private BigDecimal finePerDay;
    private BigDecimal maxFinePerInstallment;
    private int installmentsCharged;
    private int studentsAffected;
    /** Fine added by this run (0 when it is re-run for the same date) */
    private BigDecimal fineAdded;
    /** Late fines now on the branch's open dues */
    private BigDecimal totalLateFine;
}
