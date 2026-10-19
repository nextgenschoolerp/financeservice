package com.kvs.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of the fee counter's student search. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeAccountSummaryResponse {

    private UUID studentId;
    private String studentUid;
    private String studentName;
    private String gradeName;
    private String sectionName;
    private String academicYear;
    /** PAID, DUE or OVERDUE */
    private String feeStatus;
    private BigDecimal outstanding;
    private BigDecimal overdueAmount;
}
