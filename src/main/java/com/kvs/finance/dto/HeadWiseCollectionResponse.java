package com.kvs.finance.dto;

import com.kvs.finance.enums.PaymentMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** US-04017: head-wise revenue collection summary for a period. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeadWiseCollectionResponse {

    private String branchId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private int receiptCount;
    private int bouncedCount;
    private BigDecimal totalCollected;
    private BigDecimal totalDemanded;
    private BigDecimal totalOutstanding;
    private List<Head> heads;
    private List<Mode> modes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Head {
        private UUID feeHeadId;
        private String headCode;
        private String headName;
        private String ledgerCode;
        private BigDecimal collected;
        /** Share of the period's collection, in percent */
        private BigDecimal sharePercent;
        private BigDecimal demanded;
        private BigDecimal outstanding;
    }
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Mode {
        private PaymentMode paymentMode;
        private int receiptCount;
        private BigDecimal amount;
    }
}
