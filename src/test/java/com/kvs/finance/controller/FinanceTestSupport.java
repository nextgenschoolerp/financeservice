package com.kvs.finance.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kvs.core.service.JwtTokenService;
import com.kvs.finance.repo.AuditLogRepository;
import com.kvs.finance.repo.FeeAccountRepository;
import com.kvs.finance.repo.FeeHeadRepository;
import com.kvs.finance.repo.FeePaymentReceiptRepository;
import com.kvs.finance.repo.FeeReceiptDispatchRepository;
import com.kvs.finance.repo.FeeScheduleRepository;
import com.kvs.finance.repo.StudentFeeLedgerRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared fixtures: tokens shaped like authservice's (roles, tenantId, userId claims) and a Class 6 schedule with
 * Tuition (two terms) and Lab, one installment already overdue, posted to two students.
 */
abstract class FinanceTestSupport {

    static final String TENANT = "TENANT-FIN";
    static final String BRANCH = "BR-NORTH";
    static final String PARENT_ID = "parent-user-1";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JwtTokenService jwtTokenService;
    @Autowired private FeeReceiptDispatchRepository dispatchRepository;
    @Autowired private FeePaymentReceiptRepository receiptRepository;
    @Autowired private StudentFeeLedgerRepository ledgerRepository;
    @Autowired private FeeAccountRepository accountRepository;
    @Autowired private FeeScheduleRepository scheduleRepository;
    @Autowired private FeeHeadRepository headRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    protected final UUID studentA = UUID.randomUUID();
    protected final UUID studentB = UUID.randomUUID();

    @AfterEach
    void cleanDatabase() {
        dispatchRepository.deleteAll();
        receiptRepository.deleteAll();
        ledgerRepository.deleteAll();
        accountRepository.deleteAll();
        scheduleRepository.deleteAll();
        headRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    protected String token(String role) {
        return token(role, TENANT, role.toLowerCase() + "-user");
    }

    protected String token(String role, String tenantId, String userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", List.of("ROLE_" + role));
        claims.put("tenantId", tenantId);
        claims.put("userId", userId);
        return "Bearer " + jwtTokenService.generateToken(role.toLowerCase() + "@kvs.local", claims);
    }

    protected ResultActions postJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    protected JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }

    protected String createHead(String code, String name, String category) throws Exception {
        return data(postJson("/api/v1/finance/fee-heads", token("CHIEF_ACCOUNTANT"),
                Map.of("headCode", code, "headName", name, "category", category))
                .andExpect(status().isCreated())).path("feeHeadId").asText();
    }

    /** Academic year containing today (April – March). */
    protected static String academicYear() {
        LocalDate today = LocalDate.now();
        int start = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        return start + "-" + (start + 1);
    }

    /** A due date inside the current academic year, offset from today but clamped to the year. */
    protected static LocalDate dueIn(int days) {
        int start = Integer.parseInt(academicYear().substring(0, 4));
        LocalDate d = LocalDate.now().plusDays(days);
        LocalDate first = LocalDate.of(start, 4, 1);
        LocalDate last = LocalDate.of(start + 1, 3, 31);
        return d.isBefore(first) ? first : d.isAfter(last) ? last : d;
    }

    /**
     * Class 6 schedule: Tuition Term 1 18000 due 20 days ago, Tuition Term 2 18000 due in 40 days, Lab 2500 due
     * 20 days ago. Returns the schedule id.
     */
    protected String createClass6Schedule() throws Exception {
        String tuition = createHead("TUITION", "Tuition Fee", "TUITION");
        String lab = createHead("LAB", "Science Lab Fee", "LAB");
        Map<String, Object> body = Map.of(
                "branchId", BRANCH, "academicYear", academicYear(), "gradeName", "Class 6", "scheduleName", "Class 6 fees",
                "lines", List.of(
                        Map.of("feeHeadId", tuition, "installmentLabel", "Term 1", "dueDate", dueIn(-20).toString(), "amount", 18000),
                        Map.of("feeHeadId", tuition, "installmentLabel", "Term 2", "dueDate", dueIn(40).toString(), "amount", 18000),
                        Map.of("feeHeadId", lab, "installmentLabel", "Term 1", "dueDate", dueIn(-20).toString(), "amount", 2500)));
        return data(postJson("/api/v1/finance/fee-schedules", token("CHIEF_ACCOUNTANT"), body)
                .andExpect(status().isCreated())).path("scheduleId").asText();
    }

    /** Creates the Class 6 schedule and posts it to student A (linked to PARENT_ID) and student B. */
    protected String postClass6ToStudents() throws Exception {
        String scheduleId = createClass6Schedule();
        postJson("/api/v1/finance/fee-schedules/" + scheduleId + "/demands", token("CHIEF_ACCOUNTANT"), Map.of("students", List.of(
                Map.of("studentId", studentA, "studentUid", "STU-T-001", "studentName", "Asha Rao", "sectionName", "A", "parentAccountId", PARENT_ID),
                Map.of("studentId", studentB, "studentUid", "STU-T-002", "studentName", "Bilal Khan", "sectionName", "B"))))
                .andExpect(status().isCreated());
        return scheduleId;
    }

    protected String payAtCounter(UUID studentId, Object amount, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("studentId", studentId, "amount", amount, "paymentMode", "CASH"));
        body.putAll(extra);
        return data(postJson("/api/v1/finance/receipts/counter", token("FINANCE_CASHIER"), body)
                .andExpect(status().isCreated())).path("receiptId").asText();
    }
}
