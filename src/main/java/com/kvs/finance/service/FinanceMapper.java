package com.kvs.finance.service;

import com.kvs.finance.dto.FeeHeadResponse;
import com.kvs.finance.dto.FeeScheduleResponse;
import com.kvs.finance.dto.LedgerLineResponse;
import com.kvs.finance.dto.ReceiptResponse;
import com.kvs.finance.entity.FeeAccount;
import com.kvs.finance.entity.FeeHead;
import com.kvs.finance.entity.FeePaymentReceipt;
import com.kvs.finance.entity.FeeSchedule;
import com.kvs.finance.entity.FeeScheduleLine;
import com.kvs.finance.entity.StudentFeeLedger;

import java.math.BigDecimal;

/** Entity → response mapping shared by the finance services. */
final class FinanceMapper {

    private FinanceMapper() {
    }

    static FeeHeadResponse head(FeeHead h) {
        return FeeHeadResponse.builder()
                .feeHeadId(h.getFeeHeadId()).headCode(h.getHeadCode()).headName(h.getHeadName()).category(h.getCategory())
                .recurring(h.isRecurring()).ledgerCode(h.getLedgerCode()).ledgerName(h.getLedgerName()).active(h.isActive())
                .build();
    }

    static FeeScheduleResponse schedule(FeeSchedule s) {
        return FeeScheduleResponse.builder()
                .scheduleId(s.getScheduleId()).branchId(s.getBranchId()).academicYear(s.getAcademicYear())
                .gradeId(s.getGradeId()).gradeName(s.getGradeName()).scheduleName(s.getScheduleName())
                .totalAmount(s.getLines().stream().map(FeeScheduleLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .installmentCount((int) s.getLines().stream().map(FeeScheduleLine::getInstallmentLabel).distinct().count())
                .lines(s.getLines().stream().map(l -> FeeScheduleResponse.Line.builder()
                        .lineId(l.getLineId()).feeHeadId(l.getFeeHead().getFeeHeadId()).headCode(l.getFeeHead().getHeadCode())
                        .headName(l.getFeeHead().getHeadName()).category(l.getFeeHead().getCategory())
                        .installmentLabel(l.getInstallmentLabel()).dueDate(l.getDueDate()).amount(l.getAmount())
                        .build()).toList())
                .build();
    }

    static LedgerLineResponse ledger(StudentFeeLedger l) {
        return LedgerLineResponse.builder()
                .ledgerId(l.getLedgerId()).feeHeadId(l.getFeeHead().getFeeHeadId()).headCode(l.getFeeHead().getHeadCode())
                .headName(l.getFeeHead().getHeadName()).installmentLabel(l.getInstallmentLabel()).dueDate(l.getDueDate())
                .baseAmount(l.getBaseAmount()).concessionAmount(l.getConcessionAmount()).lateFineAmount(l.getLateFineAmount())
                .netPayable(l.netPayable()).paidAmount(l.getPaidAmount()).outstanding(l.outstanding())
                .paymentStatus(l.getPaymentStatus())
                .build();
    }

    static ReceiptResponse receipt(FeePaymentReceipt r) {
        FeeAccount a = r.getAccount();
        return ReceiptResponse.builder()
                .receiptId(r.getReceiptId()).receiptNumber(r.getReceiptNumber())
                .studentId(a.getStudentId()).studentUid(a.getStudentUid()).studentName(a.getStudentName())
                .gradeName(a.getGradeName()).sectionName(a.getSectionName()).branchId(r.getBranchId()).academicYear(a.getAcademicYear())
                .totalAmountPaid(r.getTotalAmountPaid()).paymentMode(r.getPaymentMode())
                .instrumentNumber(r.getInstrumentNumber()).bankName(r.getBankName()).instrumentDate(r.getInstrumentDate())
                .payerName(r.getPayerName()).remarks(r.getRemarks()).paymentTimestamp(r.getPaymentTimestamp())
                .collectedBy(r.getCollectedBy()).receiptStatus(r.getReceiptStatus())
                .bouncedAt(r.getBouncedAt()).bouncedBy(r.getBouncedBy()).bounceReason(r.getBounceReason()).bounceCharge(r.getBounceCharge())
                .pdfUrl("/api/v1/finance/receipts/" + r.getReceiptId() + "/pdf")
                .allocations(r.getAllocations().stream().map(x -> ReceiptResponse.Allocation.builder()
                        .ledgerId(x.getLedger().getLedgerId()).headName(x.getLedger().getFeeHead().getHeadName())
                        .installmentLabel(x.getLedger().getInstallmentLabel()).amount(x.getAmount())
                        .build()).toList())
                .build();
    }

    /** Sort key for class names: Nursery, LKG, UKG, Class 1 … Class 12, then anything else alphabetically. */
    static int gradeOrder(String gradeName) {
        if (gradeName == null) {
            return 1000;
        }
        String g = gradeName.trim().toLowerCase();
        switch (g) {
            case "nursery": return 0;
            case "lkg": return 1;
            case "ukg": return 2;
            default:
                String digits = g.replaceAll("\\D", "");
                return digits.isEmpty() ? 999 : 2 + Integer.parseInt(digits);
        }
    }
}
