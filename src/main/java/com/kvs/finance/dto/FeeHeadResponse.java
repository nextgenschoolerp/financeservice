package com.kvs.finance.dto;

import com.kvs.finance.enums.FeeHeadCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A fee head with its ledger mapping. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeHeadResponse {

    private UUID feeHeadId;
    private String headCode;
    private String headName;
    private FeeHeadCategory category;
    private boolean recurring;
    private String ledgerCode;
    private String ledgerName;
    private boolean active;
}
