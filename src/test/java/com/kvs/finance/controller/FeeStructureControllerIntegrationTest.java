package com.kvs.finance.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** US-04000 fee heads, schedules and demand posting; US-04018 ledger code mapping. */
@SpringBootTest
@AutoConfigureMockMvc
class FeeStructureControllerIntegrationTest extends FinanceTestSupport {

    @Test
    @DisplayName("US-04000: chief accountant creates a fee head (201) and echoes the trace id")
    void createHead() throws Exception {
        mockMvc.perform(post("/api/v1/finance/fee-heads")
                        .header("Authorization", token("CHIEF_ACCOUNTANT"))
                        .header("X-Trace-Id", "trace-fin-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headCode\":\"TRANSPORT\",\"headName\":\"Transport Fee\",\"category\":\"TRANSPORT\",\"ledgerCode\":\"4300-TRANSPORT\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "trace-fin-001"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.headCode").value("TRANSPORT"))
                .andExpect(jsonPath("$.data.recurring").value(true))
                .andExpect(jsonPath("$.data.ledgerName").value("Transport Fee"));

        mockMvc.perform(get("/api/v1/finance/fee-heads").header("Authorization", token("APPLICANT_PARENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("US-04000: fee head validation 400, duplicate code 409, no token 401, cashier 403")
    void createHeadNegatives() throws Exception {
        postJson("/api/v1/finance/fee-heads", token("CHIEF_ACCOUNTANT"), Map.of("headCode", "tuition fee", "headName", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        createHead("TUITION", "Tuition Fee", "TUITION");
        postJson("/api/v1/finance/fee-heads", token("FINANCE_CONTROLLER"), Map.of("headCode", "TUITION", "headName", "Tuition", "category", "TUITION"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/finance/fee-heads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headCode\":\"LAB\",\"headName\":\"Lab\",\"category\":\"LAB\"}"))
                .andExpect(status().isUnauthorized());
        postJson("/api/v1/finance/fee-heads", token("FINANCE_CASHIER"), Map.of("headCode", "LAB", "headName", "Lab", "category", "LAB"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("US-04018: finance controller and accounts clerk map ledger codes; bad code 400; other tenant 404; cashier 403")
    void ledgerMapping() throws Exception {
        String id = createHead("TUITION", "Tuition Fee", "TUITION");
        mockMvc.perform(patch("/api/v1/finance/fee-heads/{id}/ledger-mapping", id)
                        .header("Authorization", token("FINANCE_CONTROLLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledgerCode\":\"4100-TUITION\",\"ledgerName\":\"Tuition Fee Income\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerCode").value("4100-TUITION"))
                .andExpect(jsonPath("$.data.ledgerName").value("Tuition Fee Income"));
        mockMvc.perform(patch("/api/v1/finance/fee-heads/{id}/ledger-mapping", id)
                        .header("Authorization", token("ACCOUNTS_CLERK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledgerCode\":\"tuition income\",\"ledgerName\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/finance/fee-heads/{id}/ledger-mapping", id)
                        .header("Authorization", token("FINANCE_CONTROLLER", "TENANT-OTHER", "u"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledgerCode\":\"4100-TUITION\",\"ledgerName\":\"Tuition\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/finance/fee-heads/{id}/ledger-mapping", UUID.randomUUID())
                        .header("Authorization", token("FINANCE_CONTROLLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledgerCode\":\"4100-TUITION\",\"ledgerName\":\"Tuition\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/finance/fee-heads/{id}/ledger-mapping", id)
                        .header("Authorization", token("FINANCE_CASHIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledgerCode\":\"4100-TUITION\",\"ledgerName\":\"Tuition\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("US-04000: a schedule totals its lines; duplicate class 409; due date outside the year 400; unknown head 404")
    void schedules() throws Exception {
        String scheduleId = createClass6Schedule();
        mockMvc.perform(get("/api/v1/finance/fee-schedules/{id}", scheduleId).header("Authorization", token("FINANCE_CASHIER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(38500.0))
                .andExpect(jsonPath("$.data.installmentCount").value(2))
                .andExpect(jsonPath("$.data.lines.length()").value(3));
        mockMvc.perform(get("/api/v1/finance/fee-schedules").param("branchId", BRANCH).header("Authorization", token("SCHOOL_PRINCIPAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].gradeName").value("Class 6"));

        String head = createHead("EXAM", "Examination Fee", "EXAMINATION");
        Map<String, Object> line = Map.of("feeHeadId", head, "installmentLabel", "Annual", "dueDate", dueIn(10).toString(), "amount", 1500);
        postJson("/api/v1/finance/fee-schedules", token("CHIEF_ACCOUNTANT"), Map.of("branchId", BRANCH, "academicYear", academicYear(),
                "gradeName", "class 6", "scheduleName", "again", "lines", List.of(line)))
                .andExpect(status().isConflict());
        int start = Integer.parseInt(academicYear().substring(0, 4));
        postJson("/api/v1/finance/fee-schedules", token("CHIEF_ACCOUNTANT"), Map.of("branchId", BRANCH, "academicYear", academicYear(),
                "gradeName", "Class 7", "scheduleName", "Class 7 fees",
                "lines", List.of(Map.of("feeHeadId", head, "installmentLabel", "Annual", "dueDate", (start + 2) + "-01-10", "amount", 1500))))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/finance/fee-schedules", token("CHIEF_ACCOUNTANT"), Map.of("branchId", BRANCH, "academicYear", academicYear(),
                "gradeName", "Class 7", "scheduleName", "Class 7 fees",
                "lines", List.of(Map.of("feeHeadId", UUID.randomUUID(), "installmentLabel", "Annual", "dueDate", dueIn(10).toString(), "amount", 1500))))
                .andExpect(status().isNotFound());
        postJson("/api/v1/finance/fee-schedules", token("CHIEF_ACCOUNTANT"), Map.of("branchId", BRANCH, "academicYear", "2026",
                "gradeName", "Class 7", "scheduleName", "x", "lines", List.of()))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/finance/fee-schedules", token("ACCOUNTS_CLERK"), Map.of("branchId", BRANCH, "academicYear", academicYear(),
                "gradeName", "Class 7", "scheduleName", "Class 7 fees", "lines", List.of(line)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("US-04000: posting demands creates ledger lines once per student; re-posting skips them; unknown schedule 404")
    void postDemands() throws Exception {
        String scheduleId = createClass6Schedule();
        Map<String, Object> students = Map.of("students", List.of(
                Map.of("studentId", studentA, "studentUid", "STU-T-001", "studentName", "Asha Rao", "sectionName", "A"),
                Map.of("studentId", studentB, "studentUid", "STU-T-002", "studentName", "Bilal Khan", "sectionName", "B")));
        postJson("/api/v1/finance/fee-schedules/" + scheduleId + "/demands", token("FINANCE_CONTROLLER"), students)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.studentsPosted").value(2))
                .andExpect(jsonPath("$.data.ledgerLinesCreated").value(6))
                .andExpect(jsonPath("$.data.totalDemanded").value(77000.0));
        postJson("/api/v1/finance/fee-schedules/" + scheduleId + "/demands", token("CHIEF_ACCOUNTANT"), students)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.studentsPosted").value(0))
                .andExpect(jsonPath("$.data.studentsSkipped").value(2));
        postJson("/api/v1/finance/fee-schedules/" + UUID.randomUUID() + "/demands", token("CHIEF_ACCOUNTANT"), students)
                .andExpect(status().isNotFound());
        postJson("/api/v1/finance/fee-schedules/" + scheduleId + "/demands", token("CHIEF_ACCOUNTANT"), Map.of("students", List.of()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/finance/students/{id}/fee-account", studentA).header("Authorization", token("REGISTRAR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeStatus").value("OVERDUE"))
                .andExpect(jsonPath("$.data.totalDemanded").value(38500.0))
                .andExpect(jsonPath("$.data.overdueAmount").value(20500.0))
                .andExpect(jsonPath("$.data.nextDueAmount").value(18000.0))
                .andExpect(jsonPath("$.data.ledger.length()").value(3));
    }
}
