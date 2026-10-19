package com.kvs.finance.controller;

import com.kvs.core.dto.ApiResponse;
import com.kvs.finance.config.FinanceRoles;
import com.kvs.finance.dto.FeeAccountResponse;
import com.kvs.finance.dto.FeeAccountSummaryResponse;
import com.kvs.finance.service.FeeAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * A student's fee status: the student profile "Fee status" card, the counter look-up (US-04013) and the parent's
 * view of their child's dues and receipts (US-04004).
 */
@RestController
@RequestMapping("/api/v1/finance")
public class FeeAccountController {

    @Autowired
    private FeeAccountService feeAccountService;

    @GetMapping("/students/{studentId}/fee-account")
    @PreAuthorize(FinanceRoles.VIEW_STUDENT_FEES)
    public ResponseEntity<ApiResponse<FeeAccountResponse>> getAccount(@PathVariable("studentId") UUID studentId,
                                                                      @RequestParam(value = "academicYear", required = false) String academicYear) {
        return ResponseEntity.ok(ApiResponse.success(feeAccountService.getAccount(studentId, academicYear)));
    }

    /** US-04013: fee counter student search by name or UID */
    @GetMapping("/fee-accounts")
    @PreAuthorize(FinanceRoles.FINANCE_STAFF)
    public ResponseEntity<ApiResponse<List<FeeAccountSummaryResponse>>> search(
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "branchId", required = false) String branchId,
            @RequestParam(value = "q", required = false) String text,
            @RequestParam(value = "academicYear", required = false) String academicYear) {
        return ResponseEntity.ok(ApiResponse.success(feeAccountService.search(tenantId, branchId, text, academicYear)));
    }
}
