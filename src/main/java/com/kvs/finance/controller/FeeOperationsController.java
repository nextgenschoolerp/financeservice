package com.kvs.finance.controller;

import com.kvs.core.dto.ApiResponse;
import com.kvs.finance.config.FinanceRoles;
import com.kvs.finance.dto.AccrueLateFeesRequest;
import com.kvs.finance.dto.DefaulterReportResponse;
import com.kvs.finance.dto.HeadWiseCollectionResponse;
import com.kvs.finance.dto.LateFeeAccrualResponse;
import com.kvs.finance.service.FeeReportService;
import com.kvs.finance.service.LateFeeService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Late fees and fee reports.
 * Covers user stories:
 * - US-04005: Accrue daily late fee penalties on outstanding dues past grace period deadlines
 * - US-04007: Generate class-wise fee defaulter lists with pending balance breakdowns
 * - US-04017: Generate head-wise revenue collection summaries for board meetings
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/finance")
public class FeeOperationsController {

    @Autowired
    private LateFeeService lateFeeService;

    @Autowired
    private FeeReportService reportService;

    /** US-04005 */
    @PostMapping("/late-fees/accrue")
    @PreAuthorize(FinanceRoles.ACCRUE_LATE_FEES)
    public ResponseEntity<ApiResponse<LateFeeAccrualResponse>> accrueLateFees(@Valid @RequestBody AccrueLateFeesRequest request) {
        log.info("Late fee run for branch {} as of {}", request.getBranchId(), request.getAsOfDate());
        return ResponseEntity.ok(ApiResponse.success("Late fees accrued", lateFeeService.accrue(request)));
    }

    /** US-04007 */
    @GetMapping("/reports/defaulters")
    @PreAuthorize(FinanceRoles.FINANCE_STAFF)
    public ResponseEntity<ApiResponse<DefaulterReportResponse>> defaulters(
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "branchId", required = false) String branchId,
            @RequestParam(value = "gradeName", required = false) String gradeName,
            @RequestParam(value = "asOfDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(ApiResponse.success(reportService.defaulters(tenantId, branchId, gradeName, asOfDate)));
    }

    /** US-04017 */
    @GetMapping("/reports/head-wise-collection")
    @PreAuthorize(FinanceRoles.FINANCE_STAFF)
    public ResponseEntity<ApiResponse<HeadWiseCollectionResponse>> headWiseCollection(
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "branchId", required = false) String branchId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(reportService.headWiseCollection(tenantId, branchId, from, to)));
    }
}
