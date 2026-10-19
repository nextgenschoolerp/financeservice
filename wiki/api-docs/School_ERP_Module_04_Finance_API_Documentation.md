# API Specification: Module 04 — Finance, Fee Schedules & Billing

- **Target Microservice**: `financeservice` (Port: `8085`, context path `/financeservice`)
- **API Base Path**: `/api/v1/finance`
- **Security Standard**: Bearer JWT from authservice with role-based access control (`@PreAuthorize`) and tenant isolation (`tenantId` claim)
- **Traceability**: Micrometer distributed tracing (`X-Trace-Id`, `X-Span-Id`)
- **BRD Specification**: [`story/module4/School_ERP_Module_04_Finance_Fees_1000.md`](../../../story/module4/School_ERP_Module_04_Finance_Fees_1000.md), data model [`module4.sql`](../../../story/module4/module4.sql)
- **Postman**: [`postman/School_ERP_FinanceService.postman_collection.json`](../../postman/School_ERP_FinanceService.postman_collection.json)
- **Seed**: [`src/main/resources/db/seed/module04_finance_seed.sql`](../../src/main/resources/db/seed/module04_finance_seed.sql)

---

## 1. Executive Summary & Capabilities

The first release covers the stories needed to show a real fee status for every student and to run the fee counter:

1. **US-04000**: Fee heads (Tuition, Lab, Transport …) and itemized class fee schedules with installments, posted to students as ledger demands.
2. **US-04004**: Auto-numbered receipts (`RCPT-<year>-NNNNNN`), downloadable as PDF, queued for WhatsApp / SMS / e-mail delivery.
3. **US-04005**: Daily late fee accrual on dues past a grace period, capped per installment.
4. **US-04007**: Class-wise defaulter lists with head-wise pending balances.
5. **US-04013**: Cash, cheque and demand draft collections at the fee counter, credited to the oldest dues first.
6. **US-04017**: Head-wise revenue collection summary for a period, split by payment mode.
7. **US-04018**: Fee head to accounting ledger code mapping.
8. **US-04019**: Bounced cheques: the receipt is voided, its credit reversed and a bank charge added to the dues.

UI: the Fees & payments page and the student profile Fee status card ([`frontend/docs/financeservice-api-mapping.md`](../../../frontend/docs/financeservice-api-mapping.md)).

Not in this release: sibling / staff-ward concessions (US-04001, US-04012), the payment gateway and webhooks (US-04002, US-04003), Tally export (US-04006), refunds (US-04008), settlement reconciliation (US-04009), custom installment plans (US-04010), RTE claims (US-04011), advance adjustment (US-04014), waiver audit (US-04015) and SMS reminders (US-04016).

### Fee status rules

| Term | Rule |
|---|---|
| Net payable | `base − concession + late fine` |
| Outstanding | `net payable − paid` (never below 0) |
| Installment status | `PAID` when nothing is outstanding; `OVERDUE` when the due date has passed; `PARTIALLY_PAID` when something is paid; otherwise `PENDING` |
| Student fee status | `PAID` (nothing outstanding), `OVERDUE` (an overdue installment), `DUE` (outstanding, nothing overdue) |
| "Today" | The school's time zone, `finance.time-zone` (default `Asia/Kolkata`) |

### Actors and roles

| BRD actor | Role | Notes |
|---|---|---|
| Chief Accountant | `ROLE_CHIEF_ACCOUNTANT` | Configures fees, collects, bounces |
| Finance Controller | `ROLE_FINANCE_CONTROLLER` | Configures fees, maps ledgers |
| Accounts Clerk | `ROLE_ACCOUNTS_CLERK` | Maps ledgers, day-book, sends receipts |
| Fee Cashier | `ROLE_FINANCE_CASHIER` | Counter collections, late fees, sends receipts |
| Auditor | `ROLE_FINANCE_AUDITOR` | Reports, bounced cheques |
| Trust Treasurer | `ROLE_TRUST_TREASURER` | Counter collections, late fees, reports |
| School Principal | `ROLE_SCHOOL_PRINCIPAL` | Reports, bounced cheques |
| Parent | `ROLE_APPLICANT_PARENT` | Own children's fee account, receipts and PDFs only (matched on the `userId` claim) |
| Registrar | `ROLE_REGISTRAR` | Reads a student's fee account (student profile) |

`ROLE_FINANCE_OFFICER` and `ROLE_SUPER_ADMIN` can read everything a finance staff member can; `ROLE_SUPER_ADMIN` can also act across institutions by passing `tenantId`.

---

## 2. Security & Header Specifications

| Header Name | Type | Requirement | Description |
|---|---|---|---|
| `Authorization` | String | **Required** | `Bearer <JWT_TOKEN>` from authservice (`roles`, `tenantId`, `userId` claims) |
| `Content-Type` | String | **Required** (POST / PATCH) | `application/json` |
| `X-Trace-Id` | String | Optional | Trace correlation id, echoed in the response |
| `X-Span-Id` | String | Optional | Span correlation id, echoed in the response |

Records of another institution answer `404` as if they did not exist. A request that names another institution in `tenantId` answers `403`.

### Error dictionary (`ApiErrorResponse`)

| HTTP | `errorCode` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean validation failed; `validationErrors[]` lists `field`, `rejectedValue`, `message` |
| 400 | `BAD_REQUEST` | Business rule, e.g. amount above the outstanding, due date outside the academic year, missing `branchId` |
| 401 | `UNAUTHORIZED` | No or invalid bearer token |
| 403 | `ACCESS_DENIED` | The role may not call the endpoint (or a parent lists a branch) |
| 404 | `RESOURCE_NOT_FOUND` | Unknown id, another institution's record, or a parent asking for another family's student |
| 409 | `RESOURCE_ALREADY_EXISTS` | Duplicate fee head code or class schedule; receipt already bounced; cash receipt bounced; void receipt sent |

```json
{
  "success": false,
  "status": 400,
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/financeservice/api/v1/finance/receipts/counter",
  "traceId": "59e5adc631f849248f86827f5e968efb",
  "timestamp": "2026-10-18T13:46:37.782Z",
  "validationErrors": [
    { "field": "amount", "rejectedValue": 0, "message": "amount must be at least 1.00" }
  ]
}
```

---

## 3. Endpoints Breakdown

{expand:title=US-04000: Configure itemized fee schedules combining tuition, lab, and transport heads}

**Actor**: Chief Accountant. **Business value**: one itemized schedule per class and year removes ad-hoc fee demands and cash leakage.

### Endpoints

| Method | Path | Roles | Success |
|---|---|---|---|
| `POST` | `/api/v1/finance/fee-heads` | CHIEF_ACCOUNTANT, FINANCE_CONTROLLER, SUPER_ADMIN | `201` |
| `GET` | `/api/v1/finance/fee-heads` | finance staff, principal, registrar, parent | `200` |
| `POST` | `/api/v1/finance/fee-schedules` | CHIEF_ACCOUNTANT, FINANCE_CONTROLLER, SUPER_ADMIN | `201` |
| `GET` | `/api/v1/finance/fee-schedules?branchId=` | finance staff, principal, registrar, parent | `200` |
| `GET` | `/api/v1/finance/fee-schedules/{scheduleId}` | same | `200` |
| `POST` | `/api/v1/finance/fee-schedules/{scheduleId}/demands` | CHIEF_ACCOUNTANT, FINANCE_CONTROLLER, SUPER_ADMIN | `201` |

### Create fee head — request

| Field | Type | Rules |
|---|---|---|
| `tenantId` | String | Optional, defaults to the token's institution |
| `headCode` | String | Required, `^[A-Z][A-Z0-9_]{1,29}$`, unique per institution (`409`) |
| `headName` | String | Required, ≤ 100 |
| `category` | Enum | `TUITION`, `LAB`, `TRANSPORT`, `ADMISSION`, `EXAMINATION`, `ACTIVITY`, `OTHER` |
| `recurring` | Boolean | Default `true` |
| `ledgerCode` / `ledgerName` | String | Optional (see US-04018) |

```json
{ "headCode": "TRANSPORT", "headName": "Transport Fee", "category": "TRANSPORT", "ledgerCode": "4300-TRANSPORT" }
```

### Create fee schedule — request

| Field | Type | Rules |
|---|---|---|
| `branchId` | String | Required |
| `academicYear` | String | Required, `2026-2027` form, consecutive years |
| `gradeId` | UUID | Optional studentservice grade id |
| `gradeName` | String | Required; one schedule per class, branch and year (`409`) |
| `scheduleName` | String | Required, ≤ 150 |
| `lines[]` | Array | 1–60 lines: `feeHeadId` (active head of the institution, else `404` / `400`), `installmentLabel` (≤ 50, unique per head), `dueDate` (1 Apr – 31 Mar of the year, else `400`), `amount` (≥ 1.00, 8.2 digits) |

```json
{
  "branchId": "DPS-DELHI-NORTH",
  "academicYear": "2026-2027",
  "gradeName": "Class 6",
  "scheduleName": "Class 6 fees 2026-2027",
  "lines": [
    { "feeHeadId": "5eed0004-0001-4000-8000-000000000001", "installmentLabel": "Term 1", "dueDate": "2026-04-10", "amount": 18000 },
    { "feeHeadId": "5eed0004-0001-4000-8000-000000000002", "installmentLabel": "Annual", "dueDate": "2026-07-15", "amount": 2500 }
  ]
}
```

### Schedule — response (`201 Created`)

```json
{
  "success": true,
  "message": "Fee schedule created",
  "data": {
    "scheduleId": "5eed0004-0002-4000-8000-000000000009",
    "branchId": "DPS-DELHI-NORTH",
    "academicYear": "2026-2027",
    "gradeName": "Class 6",
    "scheduleName": "Class 6 fees 2026-2027",
    "totalAmount": 20500.00,
    "installmentCount": 2,
    "lines": [
      { "lineId": "…", "feeHeadId": "…", "headCode": "TUITION", "headName": "Tuition Fee", "category": "TUITION",
        "installmentLabel": "Term 1", "dueDate": "2026-04-10", "amount": 18000.00 }
    ]
  }
}
```

### Post demands — request and response

The students' identity comes from studentservice (`GET /api/v1/students`); finance keeps a copy on the student's fee account. Students who already have the schedule are skipped, so re-posting a class only adds newcomers. A student with fees for the year in another branch answers `409`.

```json
{ "students": [ { "studentId": "5eed0003-0005-4000-8000-000000000001", "studentUid": "STU-2026-00101",
                  "studentName": "Aarav Sharma", "sectionName": "A", "parentAccountId": "5eed0001-0001-4000-8000-000000000014" } ] }
```

```json
{ "success": true, "message": "Fee demands posted",
  "data": { "scheduleId": "…", "gradeName": "Class 6", "academicYear": "2026-2027", "studentsPosted": 1, "studentsSkipped": 0,
            "ledgerLinesCreated": 6, "totalDemanded": 61000.00, "skippedStudentIds": [] } }
```

**Audit**: `FEE_HEAD_CREATED`, `FEE_SCHEDULE_CREATED`, `FEE_DEMANDS_POSTED`.

{expand}

{expand:title=Student fee account (fee status card, counter look-up, parent view)}

`GET /api/v1/finance/students/{studentId}/fee-account?academicYear=` — finance staff, principal, registrar, parent (own children). Without `academicYear` the latest year is returned. No account answers `404` (the UI shows "No fees posted").

```json
{
  "success": true,
  "data": {
    "studentId": "5eed0003-0005-4000-8000-000000000001",
    "studentUid": "STU-2026-00101",
    "studentName": "Aarav Sharma",
    "gradeName": "Class 6", "sectionName": "A", "branchId": "DPS-DELHI-NORTH", "academicYear": "2026-2027",
    "feeStatus": "OVERDUE",
    "totalDemanded": 61000.00, "totalConcession": 0.00, "totalLateFine": 0.00, "totalPaid": 21000.00,
    "outstanding": 40000.00, "overdueAmount": 22000.00,
    "nextDueDate": "2026-12-10", "nextDueAmount": 18000.00,
    "ledger": [
      { "ledgerId": "…", "headCode": "TUITION", "headName": "Tuition Fee", "installmentLabel": "Term 2", "dueDate": "2026-08-10",
        "baseAmount": 18000.00, "concessionAmount": 0.00, "lateFineAmount": 0.00, "netPayable": 18000.00,
        "paidAmount": 0.00, "outstanding": 18000.00, "paymentStatus": "OVERDUE" }
    ],
    "receipts": [ { "receiptNumber": "RCPT-2026-000027", "totalAmountPaid": 21000.00, "paymentMode": "UPI", "receiptStatus": "ISSUED" } ]
  }
}
```

{expand}

{expand:title=US-04004: Generate auto-numbered PDF fee receipts and dispatch them via WhatsApp}

**Actor**: Parent, Chief Accountant. **Business value**: parents get an itemized, verifiable receipt for every payment.

| Method | Path | Roles | Success |
|---|---|---|---|
| `GET` | `/api/v1/finance/receipts?studentId=&academicYear=` | finance staff, principal, registrar, parent (own) | `200` |
| `GET` | `/api/v1/finance/receipts?branchId=&from=&to=` | finance staff, principal, registrar (parent `403`); default last 30 days, ≤ 1 year | `200` |
| `GET` | `/api/v1/finance/receipts/{receiptId}` | same as by student | `200` |
| `GET` | `/api/v1/finance/receipts/{receiptId}/pdf` | same | `200 application/pdf`, `attachment; filename="RCPT-2026-000091.pdf"` |
| `POST` | `/api/v1/finance/receipts/{receiptId}/dispatch` | FINANCE_CASHIER, CHIEF_ACCOUNTANT, ACCOUNTS_CLERK, APPLICANT_PARENT (own), SUPER_ADMIN | `202` |

Receipt numbers are `RCPT-<calendar year>-NNNNNN`, unique across the platform. The PDF is a one-page A4 receipt (student, class, payer, mode with cheque details, fee head lines, total; a bounced receipt is printed as void).

### Dispatch — request

| Field | Rules |
|---|---|
| `channel` | `WHATSAPP`, `SMS` or `EMAIL` |
| `recipient` | Mobile number `^\+?\d{10,15}$` (spaces removed) for WhatsApp / SMS, e-mail address for EMAIL; else `400` |

A bounced receipt cannot be sent (`409`). No messaging gateway is connected yet: the dispatch is stored as `QUEUED` for Module 09 (communicationservice).

```json
{ "success": true, "message": "Receipt queued for delivery",
  "data": { "dispatchId": "…", "receiptId": "…", "receiptNumber": "RCPT-2026-000091", "channel": "WHATSAPP",
            "recipient": "+919811100114", "status": "QUEUED", "requestedBy": "ramesh.accounts@dps.edu.in", "requestedAt": "2026-10-18T09:15:02Z" } }
```

**Audit**: `FEE_RECEIPT_DISPATCH_QUEUED`.

{expand}

{expand:title=US-04005: Accrue daily late fee penalties on outstanding dues past grace period deadlines}

**Actor**: Trust Treasurer, Fee Cashier. **Business value**: consistent late fee enforcement improves working capital.

`POST /api/v1/finance/late-fees/accrue` — TRUST_TREASURER, FINANCE_CASHIER, CHIEF_ACCOUNTANT, SUPER_ADMIN → `200`.

| Field | Rules |
|---|---|
| `branchId` | Required |
| `asOfDate` | Optional, default today, not in the future (`400`) |
| `graceDays` | 0–60 |
| `finePerDay` | 1.00–1000.00 |
| `maxFinePerInstallment` | ≥ 0; `0` = no cap |

For every installment of the branch with unpaid fee (excluding fines): fine = (days past due − grace days) × fine per day, capped. A fine only grows, so re-running for the same date adds `0`; once the fee itself is paid no more fine accrues.

```json
{ "branchId": "DPS-DELHI-NORTH", "graceDays": 15, "finePerDay": 10, "maxFinePerInstallment": 1000 }
```

```json
{ "success": true, "message": "Late fees accrued",
  "data": { "branchId": "DPS-DELHI-NORTH", "asOfDate": "2026-10-18", "graceDays": 15, "finePerDay": 10, "maxFinePerInstallment": 1000,
            "installmentsCharged": 33, "studentsAffected": 10, "fineAdded": 15240.00, "totalLateFine": 15740.00 } }
```

**Audit**: `LATE_FEES_ACCRUED`.

{expand}

{expand:title=US-04007: Generate class-wise fee defaulter lists with pending balance breakdowns}

**Actor**: School Principal, Auditor. **Business value**: targeted follow-up with families before the counter rush.

`GET /api/v1/finance/reports/defaulters?branchId=&gradeName=&asOfDate=` — finance staff and principal → `200`; no `branchId` → `400`; parent → `403`.

A defaulter has at least one installment past its due date (as of the date, default today) with money owed. Classes are ordered Nursery, LKG, UKG, Class 1 … 12; students by overdue amount.

```json
{
  "success": true,
  "data": {
    "branchId": "DPS-DELHI-NORTH", "asOfDate": "2026-10-18", "gradeName": null,
    "totalDefaulters": 10, "totalOverdue": 271000.00, "totalOutstanding": 430500.00,
    "classes": [
      { "gradeName": "Class 6", "sectionName": "A", "defaulterCount": 2, "overdue": 34000.00,
        "defaulters": [
          { "studentId": "…", "studentUid": "STU-2026-00101", "studentName": "Aarav Sharma",
            "overdueInstallments": 3, "oldestDueDate": "2026-07-15", "daysOverdue": 95,
            "overdueAmount": 22000.00, "totalOutstanding": 40000.00, "lateFine": 0.00,
            "heads": [ { "headCode": "TUITION", "headName": "Tuition Fee", "overdue": 18000.00 } ] } ] }
    ]
  }
}
```

{expand}

{expand:title=US-04013: Log offline cash and cheque collections at the front-office fee counter}

**Actor**: Trust Treasurer, Fee Cashier. **Business value**: every rupee collected at the counter is receipted and posted to the ledger at once.

`POST /api/v1/finance/receipts/counter` — FINANCE_CASHIER, TRUST_TREASURER, CHIEF_ACCOUNTANT, SUPER_ADMIN → `201`.

Finding the student first: `GET /api/v1/finance/fee-accounts?branchId=&q=&academicYear=` — finance staff and principal → `200`
(parent `403`, no `branchId` `400`). Name or UID contains `q` (case-insensitive), latest academic year unless given, at most 25:

```json
{ "success": true, "data": [ { "studentId": "5eed0003-0005-4000-8000-000000000001", "studentUid": "STU-2026-00101",
  "studentName": "Aarav Sharma", "gradeName": "Class 6", "sectionName": "A", "academicYear": "2026-2027",
  "feeStatus": "OVERDUE", "outstanding": 40000.00, "overdueAmount": 22000.00 } ] }
```

| Field | Rules |
|---|---|
| `studentId` | Required; the student's fee account must exist (`404`) |
| `academicYear` | Optional, default latest account |
| `amount` | ≥ 1.00 and ≤ outstanding (`400`; no advance payments at the counter); nothing outstanding → `409` |
| `paymentMode` | `CASH`, `CHEQUE` or `DEMAND_DRAFT` (`UPI` etc. → `400`) |
| `instrumentNumber` | 6 digits, required for cheque / DD |
| `bankName` | Required for cheque / DD |
| `instrumentDate` | Optional (default today); not post-dated, not older than 3 months |
| `payerName` / `remarks` | Optional, ≤ 150 / 500 |

The amount pays the oldest due installments first (same due date: by installment label).

```json
{ "studentId": "5eed0003-0005-4000-8000-000000000001", "amount": 20000, "paymentMode": "CHEQUE",
  "instrumentNumber": "004512", "bankName": "HDFC Bank", "payerName": "Rohit Sharma" }
```

```json
{ "success": true, "message": "Payment recorded",
  "data": { "receiptId": "…", "receiptNumber": "RCPT-2026-000094", "studentUid": "STU-2026-00101", "studentName": "Aarav Sharma",
            "gradeName": "Class 6", "sectionName": "A", "totalAmountPaid": 20000.00, "paymentMode": "CHEQUE",
            "instrumentNumber": "004512", "bankName": "HDFC Bank", "instrumentDate": "2026-10-18",
            "collectedBy": "ramesh.accounts@dps.edu.in", "receiptStatus": "ISSUED",
            "pdfUrl": "/api/v1/finance/receipts/…/pdf",
            "allocations": [ { "ledgerId": "…", "headName": "Science Lab Fee", "installmentLabel": "Annual", "amount": 2500.00 } ] } }
```

**Audit**: `FEE_COLLECTED_AT_COUNTER`.

{expand}

{expand:title=US-04017: Generate head-wise revenue collection summaries for board meetings}

**Actor**: Fee Cashier, Trust Treasurer. **Business value**: real-time revenue view per fee head for the trust board.

`GET /api/v1/finance/reports/head-wise-collection?branchId=&from=&to=` — finance staff and principal → `200`. Default period: the current month to today; at most one year (`400`); `from` after `to` → `400`.

Collected = issued receipts in the period (bounced ones excluded, counted in `bouncedCount`), split by fee head through the receipt allocations. `demanded` and `outstanding` are the branch's current totals per head.

```json
{ "success": true,
  "data": { "branchId": "DPS-DELHI-NORTH", "fromDate": "2026-04-01", "toDate": "2026-10-31", "receiptCount": 92, "bouncedCount": 1,
            "totalCollected": 1949500.00, "totalDemanded": 3081000.00, "totalOutstanding": 1132000.00,
            "heads": [ { "headCode": "TUITION", "headName": "Tuition Fee", "ledgerCode": "4100-TUITION", "collected": 1678000.00,
                         "sharePercent": 86.1, "demanded": 2580000.00, "outstanding": 902000.00 } ],
            "modes": [ { "paymentMode": "CHEQUE", "receiptCount": 31, "amount": 681000.00 } ] } }
```

{expand}

{expand:title=US-04018: Manage fee head mappings to specific accounting ledger codes}

**Actor**: Finance Controller, Accounts Clerk. **Business value**: every fee head posts to the right ledger for Tally sync.

`PATCH /api/v1/finance/fee-heads/{feeHeadId}/ledger-mapping` — FINANCE_CONTROLLER, ACCOUNTS_CLERK, CHIEF_ACCOUNTANT, SUPER_ADMIN → `200`; unknown or other institution's head → `404`.

| Field | Rules |
|---|---|
| `ledgerCode` | Required, `^[A-Z0-9][A-Z0-9-]{1,29}$`, e.g. `4100-TUITION` |
| `ledgerName` | Required, ≤ 100 |

```json
{ "ledgerCode": "4600-ACTIVITY", "ledgerName": "Activity Fee Income" }
```

**Audit**: `FEE_HEAD_LEDGER_MAPPED` with the old and new code.

{expand}

{expand:title=US-04019: Flag bounced cheque transactions and reverse corresponding ledger credits}

**Actor**: Auditor, School Principal. **Business value**: dues stay accurate when a cheque is returned unpaid.

`PATCH /api/v1/finance/receipts/{receiptId}/bounce` — FINANCE_AUDITOR, SCHOOL_PRINCIPAL, CHIEF_ACCOUNTANT, SUPER_ADMIN → `200`.

| Field | Rules |
|---|---|
| `reason` | Required, ≤ 500 |
| `bounceCharge` | Optional ≥ 0 (6.2 digits); added as a fine on the earliest installment the cheque paid |

Only `CHEQUE` / `DEMAND_DRAFT` receipts that are `ISSUED` can bounce (`409` otherwise). The receipt becomes `BOUNCED` and void (PDF marked, cannot be sent); each installment it paid gets the amount back as outstanding.

```json
{ "reason": "Returned unpaid: insufficient funds", "bounceCharge": 500 }
```

**Audit**: `FEE_CHEQUE_BOUNCED`.

{expand}

---

## 4. Seeded data (DPS-DELHI-NORTH, 2026-2027)

| What | Detail |
|---|---|
| Accounts | `chiefaccountant_meera`, `controller_deepak`, `accountsclerk_sanjay`, `auditor_kavita`; `trustee_arvind` is also Trust Treasurer; `cashier_ramesh`, `principal_anita`, `parent_rohit` from earlier seeds (password `Password@123`) |
| Fee heads | Tuition, Science Lab, Transport, Activity & Sports (no ledger code), Examination, Admission |
| Schedules | Every class: Tuition Term 1 / 2 / 3 (10 Apr, 10 Aug, 10 Dec), Activity (10 Apr), Lab Class 6+ (15 Jul), Exam Class 1+ (10 Aug) |
| Payments | 93 receipts `RCPT-2026-000001` … `000093` (cash, cheque, UPI) |
| Defaulters | Myra Kapoor, Diya Nair (nothing paid); Aarav Sharma, Om Varma, Riya Bansal, Krish Saxena, Arnav Menon, Siya Iyer (Term 2 unpaid); Vihaan Reddy (10,000 of Term 2); Kabir Singh (Term 2 cheque bounced, 500 charge) |
| Fully paid | Ishaan Sharma, Zara Khan, Saanvi Gupta (Term 3 paid in October) |
