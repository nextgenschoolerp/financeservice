package com.kvs.finance.controller;

import com.kvs.core.dto.ApiResponse;
import com.kvs.finance.config.FinanceRoles;
import com.kvs.finance.dto.CreateFeeScheduleRequest;
import com.kvs.finance.dto.FeeDemandPostingResponse;
import com.kvs.finance.dto.FeeScheduleResponse;
import com.kvs.finance.dto.PostFeeDemandsRequest;
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
 * Class fee schedules and posting them to students.
 * Covers user story US-04000: Configure itemized fee schedules combining tuition, lab, and transport heads.
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/finance/fee-schedules")
public class FeeScheduleController {

    @Autowired
    private FeeStructureService feeStructureService;

    @PostMapping
    @PreAuthorize(FinanceRoles.CONFIGURE_FEES)
    public ResponseEntity<ApiResponse<FeeScheduleResponse>> createSchedule(@Valid @RequestBody CreateFeeScheduleRequest request) {
        log.info("Request to create the {} {} fee schedule", request.getGradeName(), request.getAcademicYear());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fee schedule created", feeStructureService.createSchedule(request)));
    }

    @GetMapping
    @PreAuthorize(FinanceRoles.READ_FEE_STRUCTURE)
    public ResponseEntity<ApiResponse<List<FeeScheduleResponse>>> listSchedules(
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "branchId", required = false) String branchId) {
        return ResponseEntity.ok(ApiResponse.success(feeStructureService.listSchedules(tenantId, branchId)));
    }

    @GetMapping("/{scheduleId}")
    @PreAuthorize(FinanceRoles.READ_FEE_STRUCTURE)
    public ResponseEntity<ApiResponse<FeeScheduleResponse>> getSchedule(@PathVariable("scheduleId") UUID scheduleId) {
        return ResponseEntity.ok(ApiResponse.success(feeStructureService.getSchedule(scheduleId)));
    }

    /** Posts the schedule's installments as fee demands to the given students of the class */
    @PostMapping("/{scheduleId}/demands")
    @PreAuthorize(FinanceRoles.CONFIGURE_FEES)
    public ResponseEntity<ApiResponse<FeeDemandPostingResponse>> postDemands(@PathVariable("scheduleId") UUID scheduleId,
                                                                             @Valid @RequestBody PostFeeDemandsRequest request) {
        log.info("Request to post fee schedule {} to {} students", scheduleId, request.getStudents().size());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fee demands posted", feeStructureService.postDemands(scheduleId, request)));
    }
}
