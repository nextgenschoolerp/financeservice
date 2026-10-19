package com.kvs.finance.service;

import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.finance.dto.CreateFeeHeadRequest;
import com.kvs.finance.dto.CreateFeeScheduleRequest;
import com.kvs.finance.dto.FeeDemandPostingResponse;
import com.kvs.finance.dto.FeeHeadResponse;
import com.kvs.finance.dto.FeeScheduleResponse;
import com.kvs.finance.dto.PostFeeDemandsRequest;
import com.kvs.finance.dto.UpdateLedgerMappingRequest;
import com.kvs.finance.entity.FeeAccount;
import com.kvs.finance.entity.FeeHead;
import com.kvs.finance.entity.FeeSchedule;
import com.kvs.finance.entity.FeeScheduleLine;
import com.kvs.finance.entity.StudentFeeLedger;
import com.kvs.finance.repo.FeeAccountRepository;
import com.kvs.finance.repo.FeeHeadRepository;
import com.kvs.finance.repo.FeeScheduleRepository;
import com.kvs.finance.repo.StudentFeeLedgerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fee heads, class fee schedules and posting a schedule's demands to students.
 * Covers:
 * - US-04000: Configure itemized fee schedules combining tuition, lab, and transport heads (Chief Accountant)
 * - US-04018: Manage fee head mappings to specific accounting ledger codes (Finance Controller, Accounts Clerk)
 */
@Service
@Slf4j
@Transactional
public class FeeStructureService {

    @Autowired private FeeHeadRepository feeHeadRepository;
    @Autowired private FeeScheduleRepository scheduleRepository;
    @Autowired private FeeAccountRepository accountRepository;
    @Autowired private StudentFeeLedgerRepository ledgerRepository;
    @Autowired private FinanceAccess access;
    @Autowired private FinanceAuditService auditService;
    @Autowired private Clock clock;

    // ------------------------------------------------------------------ fee heads

    public FeeHeadResponse createHead(CreateFeeHeadRequest request) {
        String tenantId = access.tenantFor(request.getTenantId());
        if (feeHeadRepository.existsByTenantIdAndHeadCode(tenantId, request.getHeadCode())) {
            throw new ConflictException("A fee head with code " + request.getHeadCode() + " already exists");
        }
        if (request.getLedgerCode() != null && request.getLedgerName() == null) {
            request.setLedgerName(request.getHeadName());
        }
        FeeHead head = new FeeHead();
        head.setTenantId(tenantId);
        head.setHeadCode(request.getHeadCode());
        head.setHeadName(request.getHeadName().trim());
        head.setCategory(request.getCategory());
        head.setRecurring(request.getRecurring() == null || request.getRecurring());
        head.setLedgerCode(request.getLedgerCode());
        head.setLedgerName(request.getLedgerName());
        head = feeHeadRepository.save(head);
        auditService.logSuccess("FeeHead:" + head.getFeeHeadId(), "FEE_HEAD_CREATED",
                head.getHeadCode() + " " + head.getHeadName() + " (" + head.getCategory() + ")");
        log.info("Fee head {} created for tenant {}", head.getHeadCode(), tenantId);
        return FinanceMapper.head(head);
    }

    @Transactional(readOnly = true)
    public List<FeeHeadResponse> listHeads(String tenantId) {
        return feeHeadRepository.findByTenantIdOrderByHeadNameAsc(access.tenantFor(tenantId)).stream()
                .map(FinanceMapper::head).toList();
    }

    /** US-04018 */
    public FeeHeadResponse updateLedgerMapping(UUID feeHeadId, UpdateLedgerMappingRequest request) {
        FeeHead head = findHead(feeHeadId);
        String before = head.getLedgerCode();
        head.setLedgerCode(request.getLedgerCode());
        head.setLedgerName(request.getLedgerName().trim());
        auditService.logSuccess("FeeHead:" + feeHeadId, "FEE_HEAD_LEDGER_MAPPED",
                head.getHeadCode() + ": " + (before == null ? "unmapped" : before) + " -> " + request.getLedgerCode());
        return FinanceMapper.head(head);
    }

    private FeeHead findHead(UUID feeHeadId) {
        FeeHead head = feeHeadRepository.findById(feeHeadId)
                .orElseThrow(() -> new ResourceNotFoundException("FeeHead", "feeHeadId", feeHeadId));
        access.requireTenantRecord(head.getTenantId(), "FeeHead", "feeHeadId", feeHeadId);
        return head;
    }

    // ------------------------------------------------------------------ fee schedules

    public FeeScheduleResponse createSchedule(CreateFeeScheduleRequest request) {
        String tenantId = access.tenantFor(request.getTenantId());
        String gradeName = request.getGradeName().trim();
        if (scheduleRepository.existsByTenantIdAndBranchIdAndAcademicYearAndGradeNameIgnoreCase(
                tenantId, request.getBranchId(), request.getAcademicYear(), gradeName)) {
            throw new ConflictException("A fee schedule for " + gradeName + " " + request.getAcademicYear() + " already exists");
        }
        int startYear = Integer.parseInt(request.getAcademicYear().substring(0, 4));
        if (Integer.parseInt(request.getAcademicYear().substring(5)) != startYear + 1) {
            throw new BadRequestException("academicYear must span two consecutive years, e.g. 2026-2027");
        }
        // Due dates fall within the academic year (1 April – 31 March)
        LocalDate yearStart = LocalDate.of(startYear, 4, 1);
        LocalDate yearEnd = LocalDate.of(startYear + 1, 3, 31);

        FeeSchedule schedule = new FeeSchedule();
        schedule.setTenantId(tenantId);
        schedule.setBranchId(request.getBranchId());
        schedule.setAcademicYear(request.getAcademicYear());
        schedule.setGradeId(request.getGradeId());
        schedule.setGradeName(gradeName);
        schedule.setScheduleName(request.getScheduleName().trim());

        Set<String> seen = new HashSet<>();
        for (CreateFeeScheduleRequest.Line line : request.getLines()) {
            FeeHead head = findHead(line.getFeeHeadId());
            if (!head.isActive()) {
                throw new BadRequestException("Fee head " + head.getHeadCode() + " is inactive");
            }
            if (line.getDueDate().isBefore(yearStart) || line.getDueDate().isAfter(yearEnd)) {
                throw new BadRequestException("dueDate " + line.getDueDate() + " is outside the academic year "
                        + request.getAcademicYear() + " (" + yearStart + " to " + yearEnd + ")");
            }
            String installment = line.getInstallmentLabel().trim();
            if (!seen.add(head.getFeeHeadId() + "|" + installment.toLowerCase())) {
                throw new BadRequestException(head.getHeadName() + " appears twice for installment " + installment);
            }
            FeeScheduleLine l = new FeeScheduleLine();
            l.setSchedule(schedule);
            l.setFeeHead(head);
            l.setInstallmentLabel(installment);
            l.setDueDate(line.getDueDate());
            l.setAmount(line.getAmount());
            schedule.getLines().add(l);
        }
        schedule = scheduleRepository.save(schedule);
        FeeScheduleResponse response = FinanceMapper.schedule(schedule);
        auditService.logSuccess("FeeSchedule:" + schedule.getScheduleId(), "FEE_SCHEDULE_CREATED",
                gradeName + " " + schedule.getAcademicYear() + ": " + schedule.getLines().size() + " lines, total " + response.getTotalAmount());
        return response;
    }

    @Transactional(readOnly = true)
    public List<FeeScheduleResponse> listSchedules(String tenantId, String branchId) {
        return scheduleRepository.findByTenantIdAndBranchIdOrderByAcademicYearDescGradeNameAsc(access.tenantFor(tenantId), FinanceAccess.requireBranch(branchId))
                .stream().map(FinanceMapper::schedule).toList();
    }

    @Transactional(readOnly = true)
    public FeeScheduleResponse getSchedule(UUID scheduleId) {
        return FinanceMapper.schedule(findSchedule(scheduleId));
    }

    private FeeSchedule findSchedule(UUID scheduleId) {
        FeeSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("FeeSchedule", "scheduleId", scheduleId));
        access.requireTenantRecord(schedule.getTenantId(), "FeeSchedule", "scheduleId", scheduleId);
        return schedule;
    }

    /**
     * Posts every line of the schedule as a ledger demand for each student. Students who already have the schedule's
     * demands are skipped, so posting the same class again only adds newly joined students.
     */
    public FeeDemandPostingResponse postDemands(UUID scheduleId, PostFeeDemandsRequest request) {
        FeeSchedule schedule = findSchedule(scheduleId);
        if (schedule.getLines().isEmpty()) {
            throw new BadRequestException("The schedule has no lines to post");
        }
        Map<UUID, PostFeeDemandsRequest.Student> students = request.getStudents().stream()
                .collect(Collectors.toMap(PostFeeDemandsRequest.Student::getStudentId, Function.identity(), (a, b) -> a,
                        java.util.LinkedHashMap::new));
        LocalDate today = LocalDate.now(clock);
        int posted = 0;
        int lines = 0;
        BigDecimal total = BigDecimal.ZERO;
        List<UUID> skipped = new ArrayList<>();

        for (PostFeeDemandsRequest.Student s : students.values()) {
            FeeAccount account = accountRepository
                    .findByTenantIdAndStudentIdAndAcademicYear(schedule.getTenantId(), s.getStudentId(), schedule.getAcademicYear())
                    .orElseGet(FeeAccount::new);
            if (account.getAccountId() != null && !account.getBranchId().equals(schedule.getBranchId())) {
                throw new ConflictException(s.getStudentName() + " already has " + schedule.getAcademicYear()
                        + " fees in branch " + account.getBranchId());
            }
            account.setTenantId(schedule.getTenantId());
            account.setBranchId(schedule.getBranchId());
            account.setStudentId(s.getStudentId());
            account.setStudentUid(s.getStudentUid().trim());
            account.setStudentName(s.getStudentName().trim());
            account.setGradeName(schedule.getGradeName());
            account.setSectionName(s.getSectionName());
            if (s.getParentAccountId() != null) {
                account.setParentAccountId(s.getParentAccountId());
            }
            account.setAcademicYear(schedule.getAcademicYear());
            account = accountRepository.save(account);

            int added = 0;
            for (FeeScheduleLine line : schedule.getLines()) {
                if (ledgerRepository.existsByAccountAccountIdAndScheduleLineId(account.getAccountId(), line.getLineId())) {
                    continue;
                }
                StudentFeeLedger ledger = new StudentFeeLedger();
                ledger.setTenantId(schedule.getTenantId());
                ledger.setAccount(account);
                ledger.setScheduleId(schedule.getScheduleId());
                ledger.setScheduleLineId(line.getLineId());
                ledger.setFeeHead(line.getFeeHead());
                ledger.setInstallmentLabel(line.getInstallmentLabel());
                ledger.setDueDate(line.getDueDate());
                ledger.setBaseAmount(line.getAmount());
                ledger.refreshStatus(today);
                ledgerRepository.save(ledger);
                added++;
                total = total.add(line.getAmount());
            }
            if (added == 0) {
                skipped.add(s.getStudentId());
            } else {
                posted++;
                lines += added;
            }
        }
        auditService.logSuccess("FeeSchedule:" + scheduleId, "FEE_DEMANDS_POSTED",
                schedule.getGradeName() + " " + schedule.getAcademicYear() + ": " + posted + " students, " + lines
                        + " lines, total " + total + ", skipped " + skipped.size());
        return FeeDemandPostingResponse.builder()
                .scheduleId(scheduleId).gradeName(schedule.getGradeName()).academicYear(schedule.getAcademicYear())
                .studentsPosted(posted).studentsSkipped(skipped.size()).ledgerLinesCreated(lines).totalDemanded(total)
                .skippedStudentIds(skipped)
                .build();
    }
}
