package com.kvs.finance.dto;

import com.kvs.finance.enums.FeeHeadCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A fee schedule with its lines and total. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeScheduleResponse {

    private UUID scheduleId;
    private String branchId;
    private String academicYear;
    private UUID gradeId;
    private String gradeName;
    private String scheduleName;
    private BigDecimal totalAmount;
    private int installmentCount;
    private List<Line> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private UUID lineId;
        private UUID feeHeadId;
        private String headCode;
        private String headName;
        private FeeHeadCategory category;
        private String installmentLabel;
        private LocalDate dueDate;
        private BigDecimal amount;
    }
}
