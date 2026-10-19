package com.kvs.finance.service;

import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.finance.dto.FeeAccountResponse;
import com.kvs.finance.dto.FeeAccountSummaryResponse;
import com.kvs.finance.entity.FeeAccount;
import com.kvs.finance.entity.StudentFeeLedger;
import com.kvs.finance.enums.PaymentStatus;
import com.kvs.finance.repo.FeeAccountRepository;
import com.kvs.finance.repo.FeePaymentReceiptRepository;
import com.kvs.finance.repo.StudentFeeLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A student's fee status (student profile "Fee status" card, fee counter look-up and the parent's view):
 * totals, overdue amount, next installment, ledger lines and receipts.
 */
@Service
@Transactional
public class FeeAccountService {

    @Autowired private FeeAccountRepository accountRepository;
    @Autowired private StudentFeeLedgerRepository ledgerRepository;
    @Autowired private FeePaymentReceiptRepository receiptRepository;
    @Autowired private FinanceAccess access;
    @Autowired private Clock clock;

    /** The student's account for the academic year, or the latest one the caller may see. */
    public FeeAccount findAccount(UUID studentId, String academicYear) {
        return accountRepository.findByStudentIdOrderByAcademicYearDesc(studentId).stream()
                .filter(a -> academicYear == null || academicYear.isBlank() || a.getAcademicYear().equals(academicYear))
                .filter(access::canSee)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("FeeAccount", "studentId", studentId));
    }

    public FeeAccountResponse getAccount(UUID studentId, String academicYear) {
        return toResponse(findAccount(studentId, academicYear));
    }

    /**
     * Fee counter search (US-04013): students of the branch whose name or UID contains the text, with their balance;
     * at most 25, for the latest academic year unless one is given.
     */
    public List<FeeAccountSummaryResponse> search(String tenantId, String branchId, String text, String academicYear) {
        String tenant = access.tenantFor(tenantId);
        FinanceAccess.requireBranch(branchId);
        String year = academicYear == null || academicYear.isBlank() ? accountRepository.latestYear(tenant, branchId) : academicYear;
        if (year == null) {
            return List.of();
        }
        List<FeeAccount> accounts = accountRepository.search(tenant, branchId, year,
                text == null ? "" : text.trim().toLowerCase(), PageRequest.of(0, 25));
        if (accounts.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        Map<UUID, List<StudentFeeLedger>> lines = ledgerRepository
                .findByAccountAccountIdInOrderByDueDateAscInstallmentLabelAsc(accounts.stream().map(FeeAccount::getAccountId).toList())
                .stream().filter(l -> l.getPaymentStatus() != PaymentStatus.CANCELLED)
                .collect(Collectors.groupingBy(l -> l.getAccount().getAccountId()));
        return accounts.stream().map(a -> {
            List<StudentFeeLedger> own = lines.getOrDefault(a.getAccountId(), List.of());
            BigDecimal outstanding = sum(own, StudentFeeLedger::outstanding);
            BigDecimal overdue = sum(own.stream().filter(l -> l.getDueDate().isBefore(today)).toList(), StudentFeeLedger::outstanding);
            return FeeAccountSummaryResponse.builder()
                    .studentId(a.getStudentId()).studentUid(a.getStudentUid()).studentName(a.getStudentName())
                    .gradeName(a.getGradeName()).sectionName(a.getSectionName()).academicYear(a.getAcademicYear())
                    .feeStatus(outstanding.signum() == 0 ? "PAID" : overdue.signum() > 0 ? "OVERDUE" : "DUE")
                    .outstanding(outstanding).overdueAmount(overdue)
                    .build();
        }).toList();
    }

    FeeAccountResponse toResponse(FeeAccount account) {
        LocalDate today = LocalDate.now(clock);
        List<StudentFeeLedger> lines = ledgerRepository.findByAccountAccountIdInOrderByDueDateAscInstallmentLabelAsc(List.of(account.getAccountId()))
                .stream().filter(l -> l.getPaymentStatus() != PaymentStatus.CANCELLED).toList();
        lines.forEach(l -> l.refreshStatus(today));

        BigDecimal outstanding = sum(lines, StudentFeeLedger::outstanding);
        BigDecimal overdue = sum(lines.stream().filter(l -> l.getPaymentStatus() == PaymentStatus.OVERDUE).toList(),
                StudentFeeLedger::outstanding);
        StudentFeeLedger next = lines.stream().filter(l -> l.outstanding().signum() > 0 && !l.getDueDate().isBefore(today))
                .findFirst().orElse(null);
        BigDecimal nextAmount = next == null ? null : sum(lines.stream()
                .filter(l -> l.getDueDate().equals(next.getDueDate())).toList(), StudentFeeLedger::outstanding);

        return FeeAccountResponse.builder()
                .accountId(account.getAccountId()).studentId(account.getStudentId()).studentUid(account.getStudentUid())
                .studentName(account.getStudentName()).gradeName(account.getGradeName()).sectionName(account.getSectionName())
                .branchId(account.getBranchId()).academicYear(account.getAcademicYear())
                .feeStatus(outstanding.signum() == 0 ? "PAID" : overdue.signum() > 0 ? "OVERDUE" : "DUE")
                .totalDemanded(sum(lines, StudentFeeLedger::getBaseAmount))
                .totalConcession(sum(lines, StudentFeeLedger::getConcessionAmount))
                .totalLateFine(sum(lines, StudentFeeLedger::getLateFineAmount))
                .totalPaid(sum(lines, StudentFeeLedger::getPaidAmount))
                .outstanding(outstanding).overdueAmount(overdue)
                .nextDueDate(next == null ? null : next.getDueDate()).nextDueAmount(nextAmount)
                .ledger(lines.stream().map(FinanceMapper::ledger).toList())
                .receipts(receiptRepository.findByAccountAccountIdInOrderByPaymentTimestampDesc(List.of(account.getAccountId()))
                        .stream().map(FinanceMapper::receipt).toList())
                .build();
    }

    static BigDecimal sum(List<StudentFeeLedger> lines, Function<StudentFeeLedger, BigDecimal> amount) {
        return lines.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
