package com.kvs.finance.controller;

import com.kvs.core.dto.ApiResponse;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ForbiddenException;
import com.kvs.finance.config.FinanceRoles;
import com.kvs.finance.dto.BounceChequeRequest;
import com.kvs.finance.dto.CounterCollectionRequest;
import com.kvs.finance.dto.DispatchReceiptRequest;
import com.kvs.finance.dto.ReceiptDispatchResponse;
import com.kvs.finance.dto.ReceiptResponse;
import com.kvs.finance.service.FeeCollectionService;
import com.kvs.finance.service.FinanceAccess;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fee receipts.
 * Covers user stories:
 * - US-04013: Log offline cash and cheque collections at the front-office fee counter
 * - US-04004: Generate auto-numbered PDF fee receipts and dispatch them via WhatsApp
 * - US-04019: Flag bounced cheque transactions and reverse corresponding ledger credits
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/finance/receipts")
public class FeeReceiptController {

    @Autowired
    private FeeCollectionService collectionService;

    @Autowired
    private FinanceAccess access;

    /** US-04013: cash / cheque / DD payment at the fee counter; returns the auto-numbered receipt */
    @PostMapping("/counter")
    @PreAuthorize(FinanceRoles.COLLECT_AT_COUNTER)
    public ResponseEntity<ApiResponse<ReceiptResponse>> collectAtCounter(@Valid @RequestBody CounterCollectionRequest request) {
        log.info("Counter collection of {} ({}) for student {}", request.getAmount(), request.getPaymentMode(), request.getStudentId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment recorded", collectionService.collectAtCounter(request)));
    }

    /**
     * Receipts of one student ({@code studentId}), or of a branch between two dates ({@code branchId}, {@code from},
     * {@code to}; finance staff only).
     */
    @GetMapping
    @PreAuthorize(FinanceRoles.VIEW_STUDENT_FEES)
    public ResponseEntity<ApiResponse<List<ReceiptResponse>>> listReceipts(
            @RequestParam(value = "studentId", required = false) UUID studentId,
            @RequestParam(value = "academicYear", required = false) String academicYear,
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "branchId", required = false) String branchId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (studentId != null) {
            return ResponseEntity.ok(ApiResponse.success(collectionService.listForStudent(studentId, academicYear)));
        }
        if (branchId == null || branchId.isBlank()) {
            throw new BadRequestException("studentId or branchId is required");
        }
        if (access.isParentOnly()) {
            throw new ForbiddenException("Parents can only list their own children's receipts");
        }
        FinanceAccess.requireBranch(branchId);
        access.requireCampus(branchId);
        return ResponseEntity.ok(ApiResponse.success(collectionService.listForBranch(tenantId, branchId, from, to)));
    }

    @GetMapping("/{receiptId}")
    @PreAuthorize(FinanceRoles.VIEW_STUDENT_FEES)
    public ResponseEntity<ApiResponse<ReceiptResponse>> getReceipt(@PathVariable("receiptId") UUID receiptId) {
        return ResponseEntity.ok(ApiResponse.success(collectionService.getReceipt(receiptId)));
    }

    /** US-04004: the receipt as a PDF */
    @GetMapping("/{receiptId}/pdf")
    @PreAuthorize(FinanceRoles.VIEW_STUDENT_FEES)
    public ResponseEntity<byte[]> receiptPdf(@PathVariable("receiptId") UUID receiptId) {
        byte[] pdf = collectionService.receiptPdf(receiptId);
        String name = collectionService.receiptNumber(receiptId) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(pdf);
    }

    /** US-04004: send the receipt over WhatsApp, SMS or e-mail */
    @PostMapping("/{receiptId}/dispatch")
    @PreAuthorize(FinanceRoles.DISPATCH_RECEIPTS)
    public ResponseEntity<ApiResponse<ReceiptDispatchResponse>> dispatch(@PathVariable("receiptId") UUID receiptId,
                                                                         @Valid @RequestBody DispatchReceiptRequest request) {
        log.info("Request to send receipt {} via {}", receiptId, request.getChannel());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Receipt queued for delivery", collectionService.dispatch(receiptId, request)));
    }

    /** US-04019: mark a cheque / DD receipt as bounced and reverse its ledger credit */
    @PatchMapping("/{receiptId}/bounce")
    @PreAuthorize(FinanceRoles.BOUNCE_CHEQUES)
    public ResponseEntity<ApiResponse<ReceiptResponse>> markBounced(@PathVariable("receiptId") UUID receiptId,
                                                                    @Valid @RequestBody BounceChequeRequest request) {
        log.info("Request to mark receipt {} as bounced", receiptId);
        return ResponseEntity.ok(ApiResponse.success("Cheque marked as bounced and ledger credit reversed",
                collectionService.markBounced(receiptId, request)));
    }
}
