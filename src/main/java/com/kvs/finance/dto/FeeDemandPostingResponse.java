package com.kvs.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Result of posting a schedule to students. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeDemandPostingResponse {

    private UUID scheduleId;
    private String gradeName;
    private String academicYear;
    private int studentsPosted;
    private int studentsSkipped;
    private int ledgerLinesCreated;
    private BigDecimal totalDemanded;
    /** Students who already had this schedule's demands */
    private List<UUID> skippedStudentIds;
}
