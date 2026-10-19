package com.kvs.finance.controller;

import com.kvs.core.dto.ApiResponse;
import com.kvs.finance.config.FinanceRoles;
import com.kvs.finance.dto.CreateFeeHeadRequest;
import com.kvs.finance.dto.FeeHeadResponse;
import com.kvs.finance.dto.UpdateLedgerMappingRequest;
import com.kvs.finance.service.FeeStructureService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Fee heads (Tuition, Lab, Transport …) and their accounting ledger codes.
 * Covers user stories:
 * - US-04000: Configure itemized fee schedules combining tuition, lab, and transport heads
 * - US-04018: Manage fee head mappings to specific accounting ledger codes
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/finance/fee-heads")
public class FeeHeadController {

    @Autowired
    private FeeStructureService feeStructureService;

    /** US-04000: create a fee head */
    @PostMapping
    @PreAuthorize(FinanceRoles.CONFIGURE_FEES)
    public ResponseEntity<ApiResponse<FeeHeadResponse>> createHead(@Valid @RequestBody CreateFeeHeadRequest request) {
        log.info("Request to create fee head {}", request.getHeadCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fee head created", feeStructureService.createHead(request)));
    }

    /** US-04000: the institution's fee heads */
    @GetMapping
    @PreAuthorize(FinanceRoles.READ_FEE_STRUCTURE)
    public ResponseEntity<ApiResponse<List<FeeHeadResponse>>> listHeads(@RequestParam(value = "tenantId", required = false) String tenantId) {
        return ResponseEntity.ok(ApiResponse.success(feeStructureService.listHeads(tenantId)));
    }

    /** US-04018: map a fee head to an accounting ledger code */
    @PatchMapping("/{feeHeadId}/ledger-mapping")
    @PreAuthorize(FinanceRoles.MAP_LEDGERS)
    public ResponseEntity<ApiResponse<FeeHeadResponse>> updateLedgerMapping(@PathVariable("feeHeadId") UUID feeHeadId,
                                                                            @Valid @RequestBody UpdateLedgerMappingRequest request) {
        log.info("Request to map fee head {} to ledger {}", feeHeadId, request.getLedgerCode());
        return ResponseEntity.ok(ApiResponse.success("Ledger mapping updated", feeStructureService.updateLedgerMapping(feeHeadId, request)));
    }
}
