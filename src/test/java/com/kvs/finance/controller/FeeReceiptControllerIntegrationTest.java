package com.kvs.finance.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** US-04013 counter collections, US-04004 receipts (PDF, dispatch, parent view), US-04019 bounced cheques. */
@SpringBootTest
@AutoConfigureMockMvc
class FeeReceiptControllerIntegrationTest extends FinanceTestSupport {

    @BeforeEach
    void postFees() throws Exception {
        postClass6ToStudents();
    }

    @Test
    @DisplayName("US-04013: cashier takes cash; it pays the oldest dues first and gets an auto-numbered receipt")
    void cashAtCounter() throws Exception {
        String year = String.valueOf(LocalDate.now().getYear());
        mockMvc.perform(post("/api/v1/finance/receipts/counter")
                        .header("Authorization", token("FINANCE_CASHIER"))
                        .header("X-Trace-Id", "trace-fin-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + studentA + "\",\"amount\":20000,\"paymentMode\":\"CASH\",\"payerName\":\"Ravi Rao\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "trace-fin-002"))
                .andExpect(jsonPath("$.data.receiptNumber").value("RCPT-" + year + "-000001"))
                .andExpect(jsonPath("$.data.receiptStatus").value("ISSUED"))
                .andExpect(jsonPath("$.data.collectedBy").value("finance_cashier@kvs.local"))
                .andExpect(jsonPath("$.data.allocations.length()").value(2))
                .andExpect(jsonPath("$.data.pdfUrl", startsWith("/api/v1/finance/receipts/")));

        mockMvc.perform(get("/api/v1/finance/students/{id}/fee-account", studentA).header("Authorization", token("FINANCE_CASHIER")))
                .andExpect(jsonPath("$.data.totalPaid").value(20000.0))
                .andExpect(jsonPath("$.data.outstanding").value(18500.0))
                .andExpect(jsonPath("$.data.overdueAmount").value(500.0))
                .andExpect(jsonPath("$.data.receipts.length()").value(1));

        payAtCounter(studentA, 500, Map.of());
        mockMvc.perform(get("/api/v1/finance/students/{id}/fee-account", studentA).header("Authorization", token("TRUST_TREASURER")))
                .andExpect(jsonPath("$.data.feeStatus").value("DUE"))
                .andExpect(jsonPath("$.data.receipts[0].receiptNumber").value("RCPT-" + year + "-000002"));
    }

    @Test
    @DisplayName("US-04013: over-payment 400, cheque without number 400, UPI at the counter 400, registrar 403, unknown student 404")
    void counterNegatives() throws Exception {
        postJson("/api/v1/finance/receipts/counter", token("FINANCE_CASHIER"), Map.of("studentId", studentA, "amount", 50000, "paymentMode", "CASH"))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/finance/receipts/counter", token("FINANCE_CASHIER"), Map.of("studentId", studentA, "amount", 1000, "paymentMode", "CHEQUE"))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/finance/receipts/counter", token("FINANCE_CASHIER"), Map.of("studentId", studentA, "amount", 1000, "paymentMode", "UPI"))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/finance/receipts/counter", token("FINANCE_CASHIER"), Map.of("amount", 0, "paymentMode", "CASH"))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/finance/receipts/counter", token("REGISTRAR"), Map.of("studentId", studentA, "amount", 1000, "paymentMode", "CASH"))
                .andExpect(status().isForbidden());
        postJson("/api/v1/finance/receipts/counter", token("FINANCE_CASHIER"), Map.of("studentId", UUID.randomUUID(), "amount", 1000, "paymentMode", "CASH"))
                .andExpect(status().isNotFound());
        postJson("/api/v1/finance/receipts/counter", token("FINANCE_CASHIER", "TENANT-OTHER", "u"), Map.of("studentId", studentA, "amount", 1000, "paymentMode", "CASH"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("US-04004: receipt PDF downloads; dispatch sends a WhatsApp message; bad number 400; class teacher 403")
    void pdfAndDispatch() throws Exception {
        String receiptId = payAtCounter(studentA, 18000, Map.of());
        mockMvc.perform(get("/api/v1/finance/receipts/{id}/pdf", receiptId).header("Authorization", token("CHIEF_ACCOUNTANT"))
                        .accept(MediaType.APPLICATION_JSON, MediaType.ALL))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", startsWith("attachment; filename=\"RCPT-")))
                .andExpect(content().string(startsWith("%PDF-1.4")));
        postJson("/api/v1/finance/receipts/" + receiptId + "/dispatch", token("FINANCE_CASHIER"), Map.of("channel", "WHATSAPP", "recipient", "+91 98111 00114"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("SENT")) // kvs.notification.whatsapp.provider=LOG: logged, reported as sent
                .andExpect(jsonPath("$.data.recipient").value("+919811100114"));
        postJson("/api/v1/finance/receipts/" + receiptId + "/dispatch", token("FINANCE_CASHIER"), Map.of("channel", "WHATSAPP", "recipient", "call me"))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/finance/receipts/" + receiptId + "/dispatch", token("CLASS_TEACHER"), Map.of("channel", "SMS", "recipient", "+919811100114"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/finance/receipts/{id}", UUID.randomUUID()).header("Authorization", token("FINANCE_CASHIER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("US-04004: a parent sees their own child's account and receipts only")
    void parentScope() throws Exception {
        String receiptA = payAtCounter(studentA, 1000, Map.of());
        String receiptB = payAtCounter(studentB, 1000, Map.of());
        String parent = token("APPLICANT_PARENT", TENANT, PARENT_ID);
        mockMvc.perform(get("/api/v1/finance/students/{id}/fee-account", studentA).header("Authorization", parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentName").value("Asha Rao"));
        mockMvc.perform(get("/api/v1/finance/students/{id}/fee-account", studentB).header("Authorization", parent))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/finance/receipts/{id}/pdf", receiptA).header("Authorization", parent))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/finance/receipts/{id}", receiptB).header("Authorization", parent))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/finance/receipts").param("branchId", BRANCH).header("Authorization", parent))
                .andExpect(status().isForbidden());
        postJson("/api/v1/finance/receipts/" + receiptA + "/dispatch", parent, Map.of("channel", "EMAIL", "recipient", "parent@example.com"))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("US-04013: counter search finds students by name or UID with their balance; parent 403; no branch 400")
    void counterSearch() throws Exception {
        mockMvc.perform(get("/api/v1/finance/fee-accounts").param("branchId", BRANCH).param("q", "asha").header("Authorization", token("FINANCE_CASHIER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].studentUid").value("STU-T-001"))
                .andExpect(jsonPath("$.data[0].feeStatus").value("OVERDUE"))
                .andExpect(jsonPath("$.data[0].outstanding").value(38500.0));
        mockMvc.perform(get("/api/v1/finance/fee-accounts").param("branchId", BRANCH).param("q", "stu-t").header("Authorization", token("TRUST_TREASURER")))
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/v1/finance/fee-accounts").param("branchId", BRANCH).header("Authorization", token("APPLICANT_PARENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/finance/fee-accounts").header("Authorization", token("FINANCE_CASHIER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Fees page day-book: branch receipts for a period; from after to 400")
    void branchReceipts() throws Exception {
        payAtCounter(studentA, 1000, Map.of());
        payAtCounter(studentB, 2000, Map.of());
        mockMvc.perform(get("/api/v1/finance/receipts").param("branchId", BRANCH).header("Authorization", token("ACCOUNTS_CLERK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/v1/finance/receipts").param("branchId", BRANCH)
                        .param("from", "2026-10-10").param("to", "2026-10-01").header("Authorization", token("ACCOUNTS_CLERK")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/finance/receipts").header("Authorization", token("ACCOUNTS_CLERK")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("US-04019: auditor bounces a cheque; credit and bank charge go back on the dues; twice 409; cash 409; cashier 403")
    void bounceCheque() throws Exception {
        String cheque = payAtCounter(studentA, 20500, Map.of("paymentMode", "CHEQUE", "instrumentNumber", "004512",
                "bankName", "HDFC Bank", "instrumentDate", LocalDate.now().toString()));
        mockMvc.perform(get("/api/v1/finance/students/{id}/fee-account", studentA).header("Authorization", token("FINANCE_AUDITOR")))
                .andExpect(jsonPath("$.data.overdueAmount").value(0.0));

        mockMvc.perform(patch("/api/v1/finance/receipts/{id}/bounce", cheque)
                        .header("Authorization", token("FINANCE_CASHIER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Insufficient funds\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/finance/receipts/{id}/bounce", cheque)
                        .header("Authorization", token("FINANCE_AUDITOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/finance/receipts/{id}/bounce", cheque)
                        .header("Authorization", token("FINANCE_AUDITOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Insufficient funds\",\"bounceCharge\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptStatus").value("BOUNCED"))
                .andExpect(jsonPath("$.data.bouncedBy").value("finance_auditor@kvs.local"));
        mockMvc.perform(get("/api/v1/finance/students/{id}/fee-account", studentA).header("Authorization", token("FINANCE_AUDITOR")))
                .andExpect(jsonPath("$.data.totalPaid").value(0.0))
                .andExpect(jsonPath("$.data.totalLateFine").value(500.0))
                .andExpect(jsonPath("$.data.overdueAmount").value(21000.0));
        mockMvc.perform(patch("/api/v1/finance/receipts/{id}/bounce", cheque)
                        .header("Authorization", token("SCHOOL_PRINCIPAL"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\"}"))
                .andExpect(status().isConflict());
        postJson("/api/v1/finance/receipts/" + cheque + "/dispatch", token("FINANCE_CASHIER"), Map.of("channel", "SMS", "recipient", "+919811100114"))
                .andExpect(status().isConflict());

        String cash = payAtCounter(studentB, 1000, Map.of());
        mockMvc.perform(patch("/api/v1/finance/receipts/{id}/bounce", cash)
                        .header("Authorization", token("FINANCE_AUDITOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Insufficient funds\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/finance/receipts/{id}/bounce", UUID.randomUUID())
                        .header("Authorization", token("FINANCE_AUDITOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Insufficient funds\"}"))
                .andExpect(status().isNotFound());
    }
}
