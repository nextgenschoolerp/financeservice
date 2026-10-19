package com.kvs.finance.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** US-04005 late fees, US-04007 defaulter lists, US-04017 head-wise collection summary. */
@SpringBootTest
@AutoConfigureMockMvc
class FeeOperationsControllerIntegrationTest extends FinanceTestSupport {

    @BeforeEach
    void postFees() throws Exception {
        postClass6ToStudents();
    }

    @Test
    @DisplayName("US-04005: treasurer accrues late fees past the grace period, capped; a re-run adds nothing")
    void accrueLateFees() throws Exception {
        // Term 1 dues are 20 days late; 10 grace days → 10 days × 50 = 500, capped at 300 per installment
        mockMvc.perform(post("/api/v1/finance/late-fees/accrue")
                        .header("Authorization", token("TRUST_TREASURER"))
                        .header("X-Trace-Id", "trace-fin-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + BRANCH + "\",\"graceDays\":10,\"finePerDay\":50,\"maxFinePerInstallment\":300}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-fin-003"))
                .andExpect(jsonPath("$.data.installmentsCharged").value(4))
                .andExpect(jsonPath("$.data.studentsAffected").value(2))
                .andExpect(jsonPath("$.data.fineAdded").value(1200.0));
        postJson("/api/v1/finance/late-fees/accrue", token("FINANCE_CASHIER"),
                Map.of("branchId", BRANCH, "graceDays", 10, "finePerDay", 50, "maxFinePerInstallment", 300))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fineAdded").value(0.0))
                .andExpect(jsonPath("$.data.totalLateFine").value(1200.0));
        mockMvc.perform(get("/api/v1/finance/students/{id}/fee-account", studentA).header("Authorization", token("FINANCE_CASHIER")))
                .andExpect(jsonPath("$.data.totalLateFine").value(600.0))
                .andExpect(jsonPath("$.data.overdueAmount").value(21100.0));
    }

    @Test
    @DisplayName("US-04005: future date 400, missing fields 400, principal 403, no token 401")
    void lateFeeNegatives() throws Exception {
        postJson("/api/v1/finance/late-fees/accrue", token("TRUST_TREASURER"), Map.of("branchId", BRANCH, "asOfDate", LocalDate.now().plusDays(5).toString(),
                "graceDays", 10, "finePerDay", 50, "maxFinePerInstallment", 300))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/finance/late-fees/accrue", token("TRUST_TREASURER"), Map.of("branchId", BRANCH, "graceDays", 90))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/finance/late-fees/accrue", token("SCHOOL_PRINCIPAL"), Map.of("branchId", BRANCH, "graceDays", 10, "finePerDay", 50, "maxFinePerInstallment", 300))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/finance/late-fees/accrue").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("US-04007: principal lists defaulters by class with head-wise balances; paid students drop off; teacher 403")
    void defaulters() throws Exception {
        payAtCounter(studentB, 20500, Map.of());
        mockMvc.perform(get("/api/v1/finance/reports/defaulters").param("branchId", BRANCH).header("Authorization", token("SCHOOL_PRINCIPAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDefaulters").value(1))
                .andExpect(jsonPath("$.data.totalOverdue").value(20500.0))
                .andExpect(jsonPath("$.data.classes[0].gradeName").value("Class 6"))
                .andExpect(jsonPath("$.data.classes[0].sectionName").value("A"))
                .andExpect(jsonPath("$.data.classes[0].defaulters[0].studentName").value("Asha Rao"))
                .andExpect(jsonPath("$.data.classes[0].defaulters[0].overdueInstallments").value(2))
                .andExpect(jsonPath("$.data.classes[0].defaulters[0].totalOutstanding").value(38500.0))
                .andExpect(jsonPath("$.data.classes[0].defaulters[0].heads.length()").value(2));
        mockMvc.perform(get("/api/v1/finance/reports/defaulters").param("branchId", BRANCH).param("gradeName", "Class 7")
                        .header("Authorization", token("FINANCE_AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDefaulters").value(0));
        mockMvc.perform(get("/api/v1/finance/reports/defaulters").param("branchId", BRANCH).header("Authorization", token("CLASS_TEACHER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/finance/reports/defaulters").header("Authorization", token("SCHOOL_PRINCIPAL")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("US-04017: head-wise collection splits the period's receipts by fee head and mode; bad period 400; parent 403")
    void headWiseCollection() throws Exception {
        payAtCounter(studentA, 20500, Map.of());
        payAtCounter(studentB, 2000, Map.of("paymentMode", "CHEQUE", "instrumentNumber", "100200", "bankName", "SBI"));
        mockMvc.perform(get("/api/v1/finance/reports/head-wise-collection").param("branchId", BRANCH)
                        .param("from", LocalDate.now().minusDays(1).toString()).param("to", LocalDate.now().plusDays(1).toString())
                        .header("Authorization", token("TRUST_TREASURER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptCount").value(2))
                .andExpect(jsonPath("$.data.totalCollected").value(22500.0))
                .andExpect(jsonPath("$.data.totalDemanded").value(77000.0))
                .andExpect(jsonPath("$.data.heads[0].headCode").value("TUITION"))
                .andExpect(jsonPath("$.data.heads[0].collected").value(20000.0))
                .andExpect(jsonPath("$.data.heads[1].collected").value(2500.0))
                .andExpect(jsonPath("$.data.modes.length()").value(2));
        mockMvc.perform(get("/api/v1/finance/reports/head-wise-collection").param("branchId", BRANCH)
                        .param("from", "2026-01-01").param("to", "2027-06-30").header("Authorization", token("TRUST_TREASURER")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/finance/reports/head-wise-collection").param("branchId", BRANCH)
                        .header("Authorization", token("APPLICANT_PARENT")))
                .andExpect(status().isForbidden());
    }
}
