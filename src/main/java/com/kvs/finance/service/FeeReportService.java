package com.kvs.finance.service;

import com.kvs.core.exception.BadRequestException;
import com.kvs.finance.dto.DefaulterReportResponse;
import com.kvs.finance.dto.HeadWiseCollectionResponse;
import com.kvs.finance.entity.FeeAccount;
import com.kvs.finance.entity.FeeHead;
import com.kvs.finance.entity.FeePaymentReceipt;
import com.kvs.finance.entity.FeeReceiptAllocation;
import com.kvs.finance.entity.StudentFeeLedger;
import com.kvs.finance.enums.PaymentMode;
import com.kvs.finance.enums.PaymentStatus;
import com.kvs.finance.enums.ReceiptStatus;
import com.kvs.finance.repo.FeePaymentReceiptRepository;
import com.kvs.finance.repo.StudentFeeLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fee reports.
 * Covers:
 * - US-04007: Generate class-wise fee defaulter lists with pending balance breakdowns (School Principal, Auditor)
 * - US-04017: Generate head-wise revenue collection summaries for board meetings (Fee Cashier, Trust Treasurer)
 */
@Service
@Transactional(readOnly = true)
public class FeeReportService {

    @Autowired private StudentFeeLedgerRepository ledgerRepository;
    @Autowired private FeePaymentReceiptRepository receiptRepository;
    @Autowired private FinanceAccess access;
    @Autowired private Clock clock;

    /** A defaulter has at least one installment past its due date (as of the date) with money still owed. */
    public DefaulterReportResponse defaulters(String tenantId, String branchId, String gradeName, LocalDate asOfDate) {
        String tenant = access.tenantFor(tenantId);
        FinanceAccess.requireBranch(branchId);
        LocalDate asOf = asOfDate == null ? LocalDate.now(clock) : asOfDate;
        List<StudentFeeLedger> open = ledgerRepository.findOpenLines(tenant, branchId,
                        EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PARTIALLY_PAID, PaymentStatus.OVERDUE)).stream()
                .filter(l -> l.outstanding().signum() > 0)
                .filter(l -> gradeName == null || gradeName.isBlank() || l.getAccount().getGradeName().equalsIgnoreCase(gradeName.trim()))
                .toList();

        Map<UUID, List<StudentFeeLedger>> byAccount = open.stream()
                .collect(Collectors.groupingBy(l -> l.getAccount().getAccountId(), LinkedHashMap::new, Collectors.toList()));
        Map<String, List<DefaulterReportResponse.Defaulter>> byClass = new LinkedHashMap<>();
        Map<String, FeeAccount> classAccount = new LinkedHashMap<>();

        for (List<StudentFeeLedger> lines : byAccount.values()) {
            List<StudentFeeLedger> overdue = lines.stream().filter(l -> l.getDueDate().isBefore(asOf)).toList();
            if (overdue.isEmpty()) {
                continue;
            }
            FeeAccount a = lines.get(0).getAccount();
            LocalDate oldest = overdue.stream().map(StudentFeeLedger::getDueDate).min(Comparator.naturalOrder()).orElseThrow();
            Map<FeeHead, BigDecimal> heads = overdue.stream().collect(Collectors.groupingBy(StudentFeeLedger::getFeeHead,
                    LinkedHashMap::new, Collectors.reducing(BigDecimal.ZERO, StudentFeeLedger::outstanding, BigDecimal::add)));
            DefaulterReportResponse.Defaulter d = DefaulterReportResponse.Defaulter.builder()
                    .studentId(a.getStudentId()).studentUid(a.getStudentUid()).studentName(a.getStudentName())
                    .overdueInstallments(overdue.size()).oldestDueDate(oldest).daysOverdue(ChronoUnit.DAYS.between(oldest, asOf))
                    .overdueAmount(FeeAccountService.sum(overdue, StudentFeeLedger::outstanding))
                    .totalOutstanding(FeeAccountService.sum(lines, StudentFeeLedger::outstanding))
                    .lateFine(FeeAccountService.sum(lines, StudentFeeLedger::getLateFineAmount))
                    .heads(heads.entrySet().stream().map(e -> DefaulterReportResponse.HeadBalance.builder()
                            .headCode(e.getKey().getHeadCode()).headName(e.getKey().getHeadName()).overdue(e.getValue()).build()).toList())
                    .build();
            String key = a.getGradeName() + "|" + (a.getSectionName() == null ? "" : a.getSectionName());
            byClass.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
            classAccount.putIfAbsent(key, a);
        }

        List<DefaulterReportResponse.ClassGroup> classes = byClass.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<DefaulterReportResponse.Defaulter>>>comparingInt(
                                e -> FinanceMapper.gradeOrder(classAccount.get(e.getKey()).getGradeName()))
                        .thenComparing(Map.Entry::getKey))
                .map(e -> {
                    FeeAccount a = classAccount.get(e.getKey());
                    List<DefaulterReportResponse.Defaulter> list = e.getValue().stream()
                            .sorted(Comparator.comparing(DefaulterReportResponse.Defaulter::getOverdueAmount).reversed()).toList();
                    return DefaulterReportResponse.ClassGroup.builder()
                            .gradeName(a.getGradeName()).sectionName(a.getSectionName()).defaulterCount(list.size())
                            .overdue(list.stream().map(DefaulterReportResponse.Defaulter::getOverdueAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                            .defaulters(list).build();
                }).toList();

        return DefaulterReportResponse.builder()
                .branchId(branchId).asOfDate(asOf).gradeName(gradeName == null || gradeName.isBlank() ? null : gradeName.trim())
                .totalDefaulters(classes.stream().mapToInt(DefaulterReportResponse.ClassGroup::getDefaulterCount).sum())
                .totalOverdue(classes.stream().map(DefaulterReportResponse.ClassGroup::getOverdue).reduce(BigDecimal.ZERO, BigDecimal::add))
                .totalOutstanding(classes.stream().flatMap(c -> c.getDefaulters().stream())
                        .map(DefaulterReportResponse.Defaulter::getTotalOutstanding).reduce(BigDecimal.ZERO, BigDecimal::add))
                .classes(classes)
                .build();
    }

    /** Money collected per fee head between two dates (bounced cheques excluded), with each head's demand and dues. */
    public HeadWiseCollectionResponse headWiseCollection(String tenantId, String branchId, LocalDate fromDate, LocalDate toDate) {
        String tenant = access.tenantFor(tenantId);
        FinanceAccess.requireBranch(branchId);
        LocalDate today = LocalDate.now(clock);
        LocalDate to = toDate == null ? today : toDate;
        LocalDate from = fromDate == null ? to.withDayOfMonth(1) : fromDate;
        if (from.isAfter(to)) {
            throw new BadRequestException("from must not be after to");
        }
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw new BadRequestException("The period must be at most one year");
        }
        List<FeePaymentReceipt> receipts = receiptRepository.findBranchReceipts(tenant, branchId,
                from.atStartOfDay(clock.getZone()).toInstant(), to.plusDays(1).atStartOfDay(clock.getZone()).toInstant());
        List<FeePaymentReceipt> issued = receipts.stream().filter(r -> r.getReceiptStatus() == ReceiptStatus.ISSUED).toList();
        BigDecimal collected = issued.stream().map(FeePaymentReceipt::getTotalAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<UUID, BigDecimal> collectedByHead = new LinkedHashMap<>();
        for (FeePaymentReceipt r : issued) {
            for (FeeReceiptAllocation x : r.getAllocations()) {
                collectedByHead.merge(x.getLedger().getFeeHead().getFeeHeadId(), x.getAmount(), BigDecimal::add);
            }
        }
        List<StudentFeeLedger> branchLines = ledgerRepository.findBranchLines(tenant, branchId);
        Map<UUID, FeeHead> heads = new LinkedHashMap<>();
        branchLines.forEach(l -> heads.putIfAbsent(l.getFeeHead().getFeeHeadId(), l.getFeeHead()));

        List<HeadWiseCollectionResponse.Head> headRows = heads.values().stream().map(h -> {
                    List<StudentFeeLedger> lines = branchLines.stream().filter(l -> l.getFeeHead().getFeeHeadId().equals(h.getFeeHeadId())).toList();
                    BigDecimal got = collectedByHead.getOrDefault(h.getFeeHeadId(), BigDecimal.ZERO);
                    return HeadWiseCollectionResponse.Head.builder()
                            .feeHeadId(h.getFeeHeadId()).headCode(h.getHeadCode()).headName(h.getHeadName()).ledgerCode(h.getLedgerCode())
                            .collected(got)
                            .sharePercent(collected.signum() == 0 ? BigDecimal.ZERO
                                    : got.multiply(BigDecimal.valueOf(100)).divide(collected, 1, RoundingMode.HALF_UP))
                            .demanded(FeeAccountService.sum(lines, StudentFeeLedger::getBaseAmount))
                            .outstanding(FeeAccountService.sum(lines, StudentFeeLedger::outstanding))
                            .build();
                })
                .sorted(Comparator.comparing(HeadWiseCollectionResponse.Head::getCollected).reversed()
                        .thenComparing(HeadWiseCollectionResponse.Head::getHeadName))
                .toList();

        Map<PaymentMode, List<FeePaymentReceipt>> byMode = issued.stream()
                .collect(Collectors.groupingBy(FeePaymentReceipt::getPaymentMode, LinkedHashMap::new, Collectors.toList()));
        List<HeadWiseCollectionResponse.Mode> modes = byMode.entrySet().stream()
                .map(e -> HeadWiseCollectionResponse.Mode.builder().paymentMode(e.getKey()).receiptCount(e.getValue().size())
                        .amount(e.getValue().stream().map(FeePaymentReceipt::getTotalAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add))
                        .build())
                .sorted(Comparator.comparing(HeadWiseCollectionResponse.Mode::getAmount).reversed())
                .toList();

        return HeadWiseCollectionResponse.builder()
                .branchId(branchId).fromDate(from).toDate(to)
                .receiptCount(issued.size()).bouncedCount(receipts.size() - issued.size()).totalCollected(collected)
                .totalDemanded(FeeAccountService.sum(branchLines, StudentFeeLedger::getBaseAmount))
                .totalOutstanding(FeeAccountService.sum(branchLines, StudentFeeLedger::outstanding))
                .heads(headRows).modes(modes)
                .build();
    }
}
