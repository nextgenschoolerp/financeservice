package com.kvs.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** US-04007: class-wise fee defaulters with pending balance breakdowns. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaulterReportResponse {

    private String branchId;
    private LocalDate asOfDate;
    private String gradeName;
    private int totalDefaulters;
    private BigDecimal totalOverdue;
    private BigDecimal totalOutstanding;
    private List<ClassGroup> classes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassGroup {
        private String gradeName;
        private String sectionName;
        private int defaulterCount;
        private BigDecimal overdue;
        private List<Defaulter> defaulters;
    }
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Defaulter {
        private UUID studentId;
        private String studentUid;
        private String studentName;
        private int overdueInstallments;
        private LocalDate oldestDueDate;
        private long daysOverdue;
        /** Outstanding on installments past their due date */
        private BigDecimal overdueAmount;
        /** All outstanding, including installments not yet due */
        private BigDecimal totalOutstanding;
        private BigDecimal lateFine;
        private List<HeadBalance> heads;
    }
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeadBalance {
        private String headCode;
        private String headName;
        private BigDecimal overdue;
    }
}
