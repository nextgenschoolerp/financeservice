package com.kvs.finance.service;

import com.kvs.core.exception.BadRequestException;
import com.kvs.finance.dto.AccrueLateFeesRequest;
import com.kvs.finance.dto.LateFeeAccrualResponse;
import com.kvs.finance.entity.StudentFeeLedger;
import com.kvs.finance.enums.PaymentStatus;
import com.kvs.finance.repo.StudentFeeLedgerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * US-04005: Accrue daily late fee penalties on outstanding dues past grace period deadlines (Trust Treasurer, Fee Cashier).
 * <p>
 * For every installment of the branch with unpaid principal, fine = (days past due − grace days) × fine per day,
 * capped at the maximum per installment (0 = no cap). The fine only ever grows, so running it twice for the same date
 * adds nothing; once the fee itself is paid no further fine accrues.
 */
@Service
@Slf4j
@Transactional
public class LateFeeService {

    @Autowired private StudentFeeLedgerRepository ledgerRepository;
    @Autowired private FinanceAccess access;
    @Autowired private FinanceAuditService auditService;
    @Autowired private Clock clock;

    public LateFeeAccrualResponse accrue(AccrueLateFeesRequest request) {
        String tenantId = access.tenantFor(request.getTenantId());
        LocalDate today = LocalDate.now(clock);
        LocalDate asOf = request.getAsOfDate() == null ? today : request.getAsOfDate();
        if (asOf.isAfter(today)) {
            throw new BadRequestException("asOfDate must not be in the future");
        }
        BigDecimal cap = request.getMaxFinePerInstallment();
        List<StudentFeeLedger> open = ledgerRepository.findOpenLines(tenantId, request.getBranchId(),
                EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PARTIALLY_PAID, PaymentStatus.OVERDUE));

        int charged = 0;
        BigDecimal added = BigDecimal.ZERO;
        Set<UUID> students = new HashSet<>();
        for (StudentFeeLedger line : open) {
            BigDecimal unpaidFee = line.getBaseAmount().subtract(line.getConcessionAmount()).subtract(line.getPaidAmount());
            long daysLate = ChronoUnit.DAYS.between(line.getDueDate(), asOf) - request.getGraceDays();
            if (unpaidFee.signum() <= 0 || daysLate <= 0) {
                continue;
            }
            BigDecimal fine = request.getFinePerDay().multiply(BigDecimal.valueOf(daysLate));
            if (cap.signum() > 0) {
                fine = fine.min(cap);
            }
            if (fine.compareTo(line.getLateFineAmount()) > 0) {
                added = added.add(fine.subtract(line.getLateFineAmount()));
                line.setLateFineAmount(fine);
                charged++;
                students.add(line.getAccount().getStudentId());
            }
            line.refreshStatus(today);
        }
        BigDecimal totalFine = open.stream().map(StudentFeeLedger::getLateFineAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        auditService.logSuccess("Branch:" + request.getBranchId(), "LATE_FEES_ACCRUED",
                "as of " + asOf + ", grace " + request.getGraceDays() + " days, " + request.getFinePerDay() + "/day, cap " + cap
                        + ": " + charged + " installments of " + students.size() + " students, added " + added);
        log.info("Late fees for {} as of {}: {} installments, {} added", request.getBranchId(), asOf, charged, added);
        return LateFeeAccrualResponse.builder()
                .branchId(request.getBranchId()).asOfDate(asOf).graceDays(request.getGraceDays())
                .finePerDay(request.getFinePerDay()).maxFinePerInstallment(cap)
                .installmentsCharged(charged).studentsAffected(students.size()).fineAdded(added).totalLateFine(totalFine)
                .build();
    }
}
