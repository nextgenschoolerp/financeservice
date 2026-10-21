# Task Execution Record: Module 04 — Finance, Fee Schedules & Billing (first release)

Execution of the `BRD_API_Generate` skill for **Module 04** based on [`story/module4/School_ERP_Module_04_Finance_Fees_1000.md`](../story/module4/School_ERP_Module_04_Finance_Fees_1000.md), [`module4.sql`](../story/module4/module4.sql) and [`module4.md`](../story/module4/module4.md).

---

## 1. Input Specification & Target Microservice Resolution

- **User Stories**: `US-04000`, `US-04004`, `US-04005`, `US-04007`, `US-04013`, `US-04017`, `US-04018`, `US-04019` (scenario 1 of each; the BRD's 1,000 entries are 50 variations of 20 base stories)
- **Module**: `04` — Finance, Fee Schedules, Online Payments & Accounting Bridge
- **Target Microservice**: `financeservice`
- **Base Package**: `com.kvs.finance`
- **Allocated Server Port**: `8085`, context path `/financeservice` (the skill table lists 8084, which studentservice already uses; agreed with the product owner on 18 Oct 2026). Test port `18085`.
- **Database**: shared PostgreSQL database `authservice` (as authservice, admissionservice and studentservice), tables created by Hibernate `ddl-auto: update`
- **Service Lifecycle Status**:
  - [ ] Existing Microservice
  - [x] New Microservice (scaffolded via Step 0, approved by the product owner)
- **Reference Architecture**: `references/java-patterns.md`, `references/microservice-scaffolding-guide.md`; bootstrap, security, audit and tenant guard copied from `studentservice`

---

## 2. Requirement Extraction (from BRD)

| Story | Sub-module (BRD) | Actor → Role | Action |
|---|---|---|---|
| US-04000 | Multi-Tiered Fee Structure & Installment Rule Configurator | Chief Accountant → `ROLE_CHIEF_ACCOUNTANT` | Configure itemized fee schedules combining tuition, lab and transport heads |
| US-04004 | Payment Gateway Integration | Parent → `ROLE_APPLICANT_PARENT`; Chief Accountant | Generate auto-numbered PDF fee receipts and dispatch them via WhatsApp |
| US-04005 | Automated Webhook Fee Clearance & Real-Time Receipting | Trust Treasurer → `ROLE_TRUST_TREASURER`; Fee Cashier | Accrue daily late fee penalties past grace period deadlines |
| US-04007 | Daily Cash Day-Book, Counter Receipts & Bank Reconciliation | School Principal → `ROLE_SCHOOL_PRINCIPAL`; Auditor | Generate class-wise fee defaulter lists with pending balance breakdowns |
| US-04013 | Fee Heads, Distance Slab Rules & Facility Charge Mappings | Trust Treasurer; Fee Cashier → `ROLE_FINANCE_CASHIER` | Log offline cash and cheque collections at the front-office fee counter |
| US-04017 | Late Fee Accrual, Grace Periods & Defaulter Nudges | Fee Cashier; Trust Treasurer | Generate head-wise revenue collection summaries for board meetings |
| US-04018 | Tally Prime XML Export & Chart of Accounts Synchronization | Finance Controller → `ROLE_FINANCE_CONTROLLER`; Accounts Clerk → `ROLE_ACCOUNTS_CLERK` | Manage fee head mappings to accounting ledger codes |
| US-04019 | Refund Processing, Excess Fee Adjustments & Ledger Audit | Auditor → `ROLE_FINANCE_AUDITOR`; School Principal | Flag bounced cheque transactions and reverse ledger credits |

### Acceptance Criteria (each story)
- [x] **AC-1 (Privileges)**: only the story's actors (plus `SUPER_ADMIN`) may act; parents only on their own children
- [x] **AC-2 (Execution/Input)**: endpoints accept the documented payloads
- [x] **AC-3 (Validation)**: invalid input → `400 VALIDATION_FAILED` / `BAD_REQUEST`
- [x] **AC-4 (Persistence)**: ledgers, receipts and allocations updated in one transaction
- [x] **AC-5 (Audit Trail)**: `audit_logs` rows `FEE_HEAD_CREATED`, `FEE_SCHEDULE_CREATED`, `FEE_DEMANDS_POSTED`, `FEE_HEAD_LEDGER_MAPPED`, `FEE_COLLECTED_AT_COUNTER`, `FEE_RECEIPT_DISPATCH_QUEUED`, `LATE_FEES_ACCRUED`, `FEE_CHEQUE_BOUNCED`
- [x] **AC-6 (Security/Isolation)**: `401` without a token, `403` for other roles, `404` for another institution's records

---

## 3. API Contract & Architectural Design

| Method | Path | Story | Roles | Request | Response |
|---|---|---|---|---|---|
| `POST` | `/api/v1/finance/fee-heads` | US-04000 | CHIEF_ACCOUNTANT, FINANCE_CONTROLLER | `CreateFeeHeadRequest` | `FeeHeadResponse` |
| `GET` | `/api/v1/finance/fee-heads` | US-04000 | finance staff, principal, registrar, parent | – | `List<FeeHeadResponse>` |
| `PATCH` | `/api/v1/finance/fee-heads/{id}/ledger-mapping` | US-04018 | FINANCE_CONTROLLER, ACCOUNTS_CLERK, CHIEF_ACCOUNTANT | `UpdateLedgerMappingRequest` | `FeeHeadResponse` |
| `POST` | `/api/v1/finance/fee-schedules` | US-04000 | CHIEF_ACCOUNTANT, FINANCE_CONTROLLER | `CreateFeeScheduleRequest` | `FeeScheduleResponse` |
| `GET` | `/api/v1/finance/fee-schedules[/{id}]` | US-04000 | finance staff, principal, registrar, parent | – | `FeeScheduleResponse` |
| `POST` | `/api/v1/finance/fee-schedules/{id}/demands` | US-04000 | CHIEF_ACCOUNTANT, FINANCE_CONTROLLER | `PostFeeDemandsRequest` | `FeeDemandPostingResponse` |
| `GET` | `/api/v1/finance/students/{studentId}/fee-account` | US-04004 / US-04013 | finance staff, principal, registrar, parent (own) | – | `FeeAccountResponse` |
| `GET` | `/api/v1/finance/fee-accounts?branchId=&q=` | US-04013 | finance staff, principal | – | `List<FeeAccountSummaryResponse>` |
| `POST` | `/api/v1/finance/receipts/counter` | US-04013 | FINANCE_CASHIER, TRUST_TREASURER, CHIEF_ACCOUNTANT | `CounterCollectionRequest` | `ReceiptResponse` |
| `GET` | `/api/v1/finance/receipts` | US-04004 / US-04013 | by student: as fee account; by branch: staff | – | `List<ReceiptResponse>` |
| `GET` | `/api/v1/finance/receipts/{id}[/pdf]` | US-04004 | as fee account | – | `ReceiptResponse` / PDF |
| `POST` | `/api/v1/finance/receipts/{id}/dispatch` | US-04004 | FINANCE_CASHIER, CHIEF_ACCOUNTANT, ACCOUNTS_CLERK, parent (own) | `DispatchReceiptRequest` | `ReceiptDispatchResponse` |
| `PATCH` | `/api/v1/finance/receipts/{id}/bounce` | US-04019 | FINANCE_AUDITOR, SCHOOL_PRINCIPAL, CHIEF_ACCOUNTANT | `BounceChequeRequest` | `ReceiptResponse` |
| `POST` | `/api/v1/finance/late-fees/accrue` | US-04005 | TRUST_TREASURER, FINANCE_CASHIER, CHIEF_ACCOUNTANT | `AccrueLateFeesRequest` | `LateFeeAccrualResponse` |
| `GET` | `/api/v1/finance/reports/defaulters` | US-04007 | finance staff, principal | – | `DefaulterReportResponse` |
| `GET` | `/api/v1/finance/reports/head-wise-collection` | US-04017 | finance staff, principal | – | `HeadWiseCollectionResponse` |

`SUPER_ADMIN` is allowed everywhere. Role expressions: [`FinanceRoles`](src/main/java/com/kvs/finance/config/FinanceRoles.java).

### Component Mapping
- **Entities** (extend `BaseAuditEntity`): `FeeHead`, `FeeSchedule`, `FeeScheduleLine`, `FeeAccount`, `StudentFeeLedger` (module4.sql `student_fee_ledgers`), `FeePaymentReceipt` (`fee_payment_receipts`), `FeeReceiptAllocation`, `FeeReceiptDispatch`, `AuditLog`
- **Services**: `FeeStructureService`, `FeeAccountService`, `FeeCollectionService`, `LateFeeService`, `FeeReportService`, `ReceiptPdfWriter` (PDF 1.4 without a library), `FinanceAccess` / `TenantAccessGuard` (tenant and parent scoping), `FinanceAuditService`
- **Controllers**: `FeeHeadController`, `FeeScheduleController`, `FeeAccountController`, `FeeReceiptController`, `FeeOperationsController`
- **Integration tests**: `FeeStructureControllerIntegrationTest`, `FeeReceiptControllerIntegrationTest`, `FeeOperationsControllerIntegrationTest` (shared fixtures in `FinanceTestSupport`)

### Design notes
- Student identity (UID, name, class, section, parent account) is copied onto `fee_accounts` when demands are posted, so finance does not call studentservice at runtime.
- `module4.sql` computes `net_payable_amount` as a generated column; here it is computed in Java (`StudentFeeLedger.netPayable()`), so the schema also runs on H2 in tests.
- Payment gateway receipts (`UPI`, `CARD`, `NET_BANKING`) exist in the enum and the seed; the gateway and webhook stories (US-04002, US-04003) are not built yet.

---

## 4. Implementation Checklist

### Step 0: Microservice Scaffolding
- [x] Directory structure, `postman/`, `wiki/api-docs/`
- [x] `build.gradle` (Spring Boot 3.2.5, Java 21, ms-core), `settings.gradle`
- [x] Gradle wrapper copied
- [x] `application.yml` (port 8085, context path, shared DB, JWT, tracing, `finance.time-zone`)
- [x] `FinanceServiceApplication`, `WebSecurityConfig`, `JpaAuditConfig`, `FinanceClockConfig`
- [x] `gradlew compileJava` clean

### Steps 1–6
- [x] Entities with tenant anchors, unique keys (`uk_fee_head_tenant_code`, `uk_fee_schedule_class_year`, `uk_fee_account_student_year`, `uk_fee_ledger_account_line`, `uk_fee_receipt_number`) and indexes
- [x] Request DTOs with Jakarta validation; response DTOs with Lombok builders
- [x] Repositories with tenant / branch queries
- [x] Services with domain exceptions and audit entries
- [x] Controllers with `@PreAuthorize`, `@Valid`, `ApiResponse<T>`
- [x] Integration tests: 201/200, 400, 401, 403, 404, 409, trace header echo — **16 tests, 0 failures**

### Step 7: Postman
- [x] [`postman/School_ERP_FinanceService.postman_collection.json`](postman/School_ERP_FinanceService.postman_collection.json): "00 - Sign in" + one folder per story (61 requests), token variable per role, ids chained; JSON and variables validated

### UI mapping
- [x] Vite proxy `/api/v1/finance` → `http://localhost:8085/financeservice` (`FINANCE_SERVICE_URL`)
- [x] Fees & payments page live for finance staff and principals; student profile Fee status card and Fees & payments tab live — [`frontend/docs/financeservice-api-mapping.md`](../frontend/docs/financeservice-api-mapping.md)

### Step 8: API Wiki
- [x] [`wiki/api-docs/School_ERP_Module_04_Finance_API_Documentation.md`](wiki/api-docs/School_ERP_Module_04_Finance_API_Documentation.md) with `{expand}` blocks per story, roles, schemas, error dictionary and seed summary

### Seed
- [x] [`src/main/resources/db/seed/module04_finance_seed.sql`](src/main/resources/db/seed/module04_finance_seed.sql): roles, 4 staff accounts, fee heads, 15 class schedules, 50 student accounts, 93 receipts, defaulters and a bounced cheque (re-runnable)

---

## 5. Verification & Quality Gates

```bash
cd financeservice
.\gradlew.bat test
```

- [x] 16 tests, 0 failures, 0 ignored
- [x] UI regression `node .agents/skills/test-ui/scripts/api-regression.mjs --service financeservice`: 46 cases (FIN-01 … FIN-46) pass; browser check of the Fees page and the student profile
- [x] Seed applied to the dev database and checked through the API on the test port (defaulters, head-wise summary, parent view, receipt PDF)

## 6. Not yet implemented (Module 04 backlog)

US-04001 sibling discounts, US-04002 online payments (UPI QR / gateway), US-04003 webhook clearance, US-04006 Tally XML export, US-04008 refunds, US-04009 settlement reconciliation, US-04010 custom installment plans, US-04011 RTE subsidy claims, US-04012 staff-ward waivers, US-04014 advance adjustment, US-04015 waiver audit, US-04016 SMS reminders.
