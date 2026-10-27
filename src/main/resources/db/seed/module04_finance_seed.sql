-- =====================================================================================================
-- Module 04 (financeservice) seed: Delhi Public School fees for 2026-2027
-- =====================================================================================================
-- Run after the Module 01 and Module 03 seeds, and after financeservice has started once against the
-- database (Hibernate ddl-auto creates the fee_* tables):
--     powershell -ExecutionPolicy Bypass -File .agents/skills/test-ui/scripts/db/db.ps1 -Script <this file>
--
-- Adds:
--   * the Module 04 roles CHIEF_ACCOUNTANT, FINANCE_CONTROLLER, ACCOUNTS_CLERK, FINANCE_AUDITOR, TRUST_TREASURER and one
--     DPS North account per role (users 5eed0001-0001-4000-8000-000000000029 … 32); trustee_arvind also gets TRUST_TREASURER.
--     Fee Cashier = FINANCE_CASHIER (cashier_ramesh), School Principal = SCHOOL_PRINCIPAL (principal_anita),
--     Parent = APPLICANT_PARENT (parent_rohit) already exist.
--
-- Story coverage (DPS-DELHI-NORTH, academic year 2026-2027):
--   US-04000 fee heads Tuition, Lab, Transport, Activity, Exam, Admission; one fee schedule per class (Nursery … Class 12):
--            Tuition in three terms (due 10 Apr, 10 Aug, 10 Dec 2026), Activity (10 Apr), Lab for Class 6+ (15 Jul),
--            Exam for Class 1+ (10 Aug); every active student (50) has the schedule of their class posted
--   US-04018 every head mapped to a ledger code except ACTIVITY (left unmapped to map in the UI)
--   US-04013 counter receipts (cash, cheque) and online ones (UPI) for Term 1 (April) and Term 2 (August), numbered
--            RCPT-2026-000001 onwards; three October receipts (Ishaan and Zara pay ahead for Term 3, Saanvi pays Term 3)
--   US-04007 defaulters: Myra Kapoor and Diya Nair have paid nothing; Aarav Sharma (parent_rohit), Om Varma, Riya Bansal,
--            Krish Saxena, Arnav Menon and Siya Iyer have not paid Term 2; Vihaan Reddy paid 10,000 of Term 2
--   US-04019 Kabir Singh's Term 2 cheque (No. 004512, HDFC Bank) bounced on 5 Oct with a 500 bank charge
--   US-04004 every receipt can be downloaded as a PDF; Ishaan (Class 9-A) is fully paid
--
-- Re-runnable: every row is marked created_by = 'seed:module04' and every seed UUID starts with 5eed0004-.
-- The script deletes those rows first, plus receipts and ledger lines later added to the seeded accounts through the API.
-- =====================================================================================================

BEGIN;

-- ---------------------------------------------------------------------------------------------------
-- 0. Clean previous seed rows (children first)
-- ---------------------------------------------------------------------------------------------------
-- receipts and ledger lines of the seeded accounts, including ones added later through the API
DELETE FROM fee_receipt_dispatches  WHERE receipt_id IN (SELECT receipt_id FROM fee_payment_receipts WHERE account_id::text LIKE '5eed0004-%');
DELETE FROM fee_receipt_allocations WHERE receipt_id IN (SELECT receipt_id FROM fee_payment_receipts WHERE account_id::text LIKE '5eed0004-%')
                                       OR ledger_id IN (SELECT ledger_id FROM student_fee_ledgers WHERE account_id::text LIKE '5eed0004-%');
DELETE FROM fee_payment_receipts    WHERE account_id::text   LIKE '5eed0004-%';
DELETE FROM student_fee_ledgers     WHERE account_id::text   LIKE '5eed0004-%';
DELETE FROM fee_accounts            WHERE account_id::text   LIKE '5eed0004-%';
DELETE FROM fee_schedule_lines      WHERE line_id::text      LIKE '5eed0004-%';
DELETE FROM fee_schedules           WHERE schedule_id::text  LIKE '5eed0004-%';
DELETE FROM fee_heads               WHERE fee_head_id::text  LIKE '5eed0004-%';
DELETE FROM user_roles              WHERE created_by = 'seed:module04';
DELETE FROM user_credentials        WHERE created_by = 'seed:module04';
DELETE FROM users                   WHERE created_by = 'seed:module04';
DELETE FROM roles                   WHERE created_by = 'seed:module04';

-- ---------------------------------------------------------------------------------------------------
-- 1. Module 04 roles and staff accounts
-- ---------------------------------------------------------------------------------------------------
INSERT INTO roles (name, description, active, created_at, updated_at, created_by, updated_by, version)
SELECT r.name, r.description, true, TIMESTAMPTZ '2026-03-01 04:30:00+00', TIMESTAMPTZ '2026-03-01 04:30:00+00', 'seed:module04', NULL, 0
FROM (VALUES
    ('CHIEF_ACCOUNTANT',   'Configures fee heads and class fee schedules and posts fee demands'),
    ('FINANCE_CONTROLLER', 'Approves fee structures and maps fee heads to accounting ledgers'),
    ('ACCOUNTS_CLERK',     'Maintains ledger mappings, receipts and day-books'),
    ('FINANCE_AUDITOR',    'Audits receipts, flags bounced cheques and reviews defaulter lists'),
    ('TRUST_TREASURER',    'Collects fees, runs late fee accrual and reviews collection summaries')
) AS r(name, description)
WHERE NOT EXISTS (SELECT 1 FROM roles x WHERE x.name = r.name);

CREATE TEMP TABLE seed_staff (user_id text, username text, first_name text, last_name text, email text, phone text, user_type text,
                              created_at timestamptz) ON COMMIT DROP;
INSERT INTO seed_staff VALUES
('5eed0001-0001-4000-8000-000000000029', 'chiefaccountant_meera', 'Meera',  'Bhatia',  'meera.bhatia@dps.edu.in',  '+919811100129', 'CHIEF_ACCOUNTANT',   '2026-03-01 05:00:00+00'),
('5eed0001-0001-4000-8000-000000000030', 'controller_deepak',     'Deepak', 'Malhotra','deepak.malhotra@dps.edu.in','+919811100130', 'FINANCE_CONTROLLER', '2026-03-01 05:05:00+00'),
('5eed0001-0001-4000-8000-000000000031', 'accountsclerk_sanjay',  'Sanjay', 'Kumar',   'sanjay.kumar@dps.edu.in',  '+919811100131', 'ACCOUNTS_CLERK',     '2026-03-01 05:10:00+00'),
('5eed0001-0001-4000-8000-000000000032', 'auditor_kavita',        'Kavita', 'Srinivasan','kavita.srinivasan@dps.edu.in','+919811100132', 'FINANCE_AUDITOR', '2026-03-01 05:15:00+00');

INSERT INTO users (user_id, tenant_id, branch_id, username, first_name, last_name, email, phone_number, user_type, is_active, is_mfa_enabled,
                   created_at, updated_at, created_by, updated_by, version)
SELECT user_id, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', username, first_name, last_name, email, phone, user_type, true, false,
       created_at, created_at, 'seed:module04', NULL, 0
FROM seed_staff;

INSERT INTO user_credentials (user_id, password_hash, password_salt, failed_login_attempts, locked_until, must_change_password,
                              password_changed_at, created_at, updated_at, created_by, updated_by, version)
SELECT user_id, '$2a$10$8liDxOW98AN4N7eJQbqKd.LMq6EZ7rg6Q11jCO3sxeqrp39CXcXTq',                 -- Password@123
       'salt_' || substr(md5(user_id), 1, 8), 0, NULL, false, created_at + interval '10 minutes',
       created_at, created_at, 'seed:module04', NULL, 0
FROM seed_staff;

INSERT INTO user_roles (user_id, role_code, created_at, updated_at, created_by, updated_by, version)
SELECT user_id, user_type, created_at, created_at, 'seed:module04', NULL, 0 FROM seed_staff
UNION ALL
-- trustee_arvind (module 01 seed) is the trust treasurer
SELECT '5eed0001-0001-4000-8000-000000000001', 'TRUST_TREASURER', TIMESTAMPTZ '2026-03-01 05:20:00+00', TIMESTAMPTZ '2026-03-01 05:20:00+00', 'seed:module04', NULL, 0;

-- ---------------------------------------------------------------------------------------------------
-- 2. Fee heads (US-04000, US-04018)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO fee_heads (fee_head_id, tenant_id, head_code, head_name, category, is_recurring, ledger_code, ledger_name, is_active,
                       created_at, updated_at, created_by, updated_by, version)
SELECT ('5eed0004-0001-4000-8000-0000000000' || h.n)::uuid, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', h.code, h.name, h.category, h.recurring,
       h.ledger_code, h.ledger_name, true, TIMESTAMPTZ '2026-03-02 05:00:00+00', TIMESTAMPTZ '2026-03-02 05:00:00+00', 'seed:module04', NULL, 0
FROM (VALUES
    ('01', 'TUITION',   'Tuition Fee',           'TUITION',     true,  '4100-TUITION',   'Tuition Fee Income'),
    ('02', 'LAB',       'Science Lab Fee',       'LAB',         true,  '4200-LAB',       'Laboratory Fee Income'),
    ('03', 'TRANSPORT', 'Transport Fee',         'TRANSPORT',   true,  '4300-TRANSPORT', 'Transport Fee Income'),
    ('04', 'ACTIVITY',  'Activity & Sports Fee', 'ACTIVITY',    true,  NULL,             NULL),
    ('05', 'EXAM',      'Examination Fee',       'EXAMINATION', true,  '4400-EXAM',      'Examination Fee Income'),
    ('06', 'ADMISSION', 'Admission Fee',         'ADMISSION',   false, '4500-ADMISSION', 'Admission Fee Income')
) AS h(n, code, name, category, recurring, ledger_code, ledger_name);

-- ---------------------------------------------------------------------------------------------------
-- 3. Class fee schedules 2026-2027 (US-04000)
--    schedule ids 5eed0004-0002-4000-8000-0000000000GG (GG = grade sequence 01 Nursery … 15 Class 12)
--    line ids     5eed0004-0003-4000-8000-00000000GGLL (LL: 01-03 Tuition terms, 04 Activity, 05 Lab, 06 Exam)
-- ---------------------------------------------------------------------------------------------------
CREATE TEMP TABLE seed_grade ON COMMIT DROP AS
SELECT g.grade_id, g.grade_name, g.sequence_order AS seq,
       CASE WHEN g.sequence_order <= 3 THEN 12000 WHEN g.sequence_order <= 8 THEN 15000 WHEN g.sequence_order <= 11 THEN 18000
            WHEN g.sequence_order <= 13 THEN 21000 ELSE 24000 END AS tuition
FROM grades g WHERE g.grade_id::text LIKE '5eed0003-0002-%';

INSERT INTO fee_schedules (schedule_id, tenant_id, branch_id, academic_year, grade_id, grade_name, schedule_name,
                           created_at, updated_at, created_by, updated_by, version)
SELECT ('5eed0004-0002-4000-8000-0000000000' || lpad(seq::text, 2, '0'))::uuid, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH',
       '2026-2027', grade_id, grade_name, grade_name || ' fees 2026-2027',
       TIMESTAMPTZ '2026-03-05 05:00:00+00', TIMESTAMPTZ '2026-03-05 05:00:00+00', 'seed:module04', NULL, 0
FROM seed_grade;

CREATE TEMP TABLE seed_line ON COMMIT DROP AS
SELECT g.seq, l.ll, ('5eed0004-0003-4000-8000-00000000' || lpad(g.seq::text, 2, '0') || l.ll)::uuid AS line_id,
       ('5eed0004-0001-4000-8000-0000000000' || l.head)::uuid AS fee_head_id, l.label, l.due::date AS due_date,
       (CASE l.ll WHEN '04' THEN 3000 WHEN '05' THEN CASE WHEN g.seq >= 14 THEN 4000 ELSE 2500 END WHEN '06' THEN 1500 ELSE g.tuition END)::numeric(10,2) AS amount
FROM seed_grade g
CROSS JOIN (VALUES ('01', '01', 'Term 1', '2026-04-10'), ('02', '01', 'Term 2', '2026-08-10'), ('03', '01', 'Term 3', '2026-12-10'),
                   ('04', '04', 'Annual', '2026-04-10'), ('05', '02', 'Annual', '2026-07-15'), ('06', '05', 'Term 2', '2026-08-10'))
     AS l(ll, head, label, due)
WHERE (l.ll <> '05' OR g.seq >= 9)      -- lab from Class 6
  AND (l.ll <> '06' OR g.seq >= 4);     -- exam fee from Class 1

INSERT INTO fee_schedule_lines (line_id, schedule_id, fee_head_id, installment_label, due_date, amount,
                                created_at, updated_at, created_by, updated_by, version)
SELECT line_id, ('5eed0004-0002-4000-8000-0000000000' || lpad(seq::text, 2, '0'))::uuid, fee_head_id, label, due_date, amount,
       TIMESTAMPTZ '2026-03-05 05:00:00+00', TIMESTAMPTZ '2026-03-05 05:00:00+00', 'seed:module04', NULL, 0
FROM seed_line;

-- ---------------------------------------------------------------------------------------------------
-- 4. Fee accounts and ledger demands for every active DPS North student
--    account ids 5eed0004-0004-4000-8000-0000000000NN (NN = student order by UID)
--    ledger ids  5eed0004-0005-4000-00NN-00000000GGLL
-- ---------------------------------------------------------------------------------------------------
CREATE TEMP TABLE seed_account ON COMMIT DROP AS
SELECT row_number() OVER (ORDER BY s.student_uid) AS rn, s.student_id, s.student_uid, s.first_name || ' ' || s.last_name AS student_name,
       g.seq, g.grade_name, sc.section_name, s.parent_account_id,
       CASE s.student_uid
           WHEN 'STU-2021-00038' THEN 'NONE'        -- Myra Kapoor
           WHEN 'STU-2017-00235' THEN 'NONE'        -- Diya Nair
           WHEN 'STU-2026-00101' THEN 'TERM1'       -- Aarav Sharma (parent_rohit)
           WHEN 'STU-2024-00253' THEN 'TERM1'       -- Om Varma
           WHEN 'STU-2024-00242' THEN 'TERM1'       -- Riya Bansal
           WHEN 'STU-2015-00244' THEN 'TERM1'       -- Krish Saxena
           WHEN 'STU-2017-00246' THEN 'TERM1'       -- Arnav Menon
           WHEN 'STU-2022-00251' THEN 'TERM1'       -- Siya Iyer
           WHEN 'STU-2021-00032' THEN 'PARTIAL'     -- Vihaan Reddy: 10,000 of Term 2
           WHEN 'STU-2020-00027' THEN 'BOUNCED'     -- Kabir Singh: Term 2 cheque bounced
           WHEN 'STU-2018-00012' THEN 'ALL'         -- Ishaan Sharma: paid Term 3 ahead (3 Oct)
           WHEN 'STU-2016-00011' THEN 'ALL'         -- Zara Khan: paid Term 3 ahead by cheque (8 Oct)
           WHEN 'STU-2022-00045' THEN 'ALL'         -- Saanvi Gupta: paid Term 3 (15 Oct)
           ELSE 'TERM2' END AS plan
FROM student_master s
JOIN seed_grade g ON g.grade_id = s.grade_id
LEFT JOIN sections sc ON sc.section_id = s.section_id
WHERE s.tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' AND s.branch_id = 'DPS-DELHI-NORTH' AND s.student_status = 'ACTIVE';

INSERT INTO fee_accounts (account_id, tenant_id, branch_id, student_id, student_uid, student_name, grade_name, section_name,
                          parent_account_id, academic_year, created_at, updated_at, created_by, updated_by, version)
SELECT ('5eed0004-0004-4000-8000-0000000000' || lpad(rn::text, 2, '0'))::uuid, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH',
       student_id, student_uid, student_name, grade_name, section_name, parent_account_id, '2026-2027',
       TIMESTAMPTZ '2026-03-20 05:00:00+00', TIMESTAMPTZ '2026-03-20 05:00:00+00', 'seed:module04', NULL, 0
FROM seed_account;

CREATE TEMP TABLE seed_ledger ON COMMIT DROP AS
SELECT a.rn, a.plan, l.ll, l.due_date, l.amount, l.line_id, l.fee_head_id, l.label,
       ('5eed0004-0005-4000-00' || lpad(a.rn::text, 2, '0') || '-00000000' || lpad(a.seq::text, 2, '0') || l.ll)::uuid AS ledger_id,
       ('5eed0004-0004-4000-8000-0000000000' || lpad(a.rn::text, 2, '0'))::uuid AS account_id,
       ('5eed0004-0002-4000-8000-0000000000' || lpad(a.seq::text, 2, '0'))::uuid AS schedule_id,
       -- payment bucket: 1 = April receipt, 2 = August receipt, 3 = October receipt
       CASE WHEN l.due_date <= DATE '2026-04-10' THEN 1 WHEN l.due_date <= DATE '2026-08-10' THEN 2 ELSE 3 END AS bucket
FROM seed_account a JOIN seed_line l ON l.seq = a.seq;

-- ---------------------------------------------------------------------------------------------------
-- 5. Receipts (US-04013, US-04004, US-04019) and their allocations
--    receipt ids 5eed0004-0006-4000-8000-00000000NNNB (NN = account, B = bucket); numbered by payment time
-- ---------------------------------------------------------------------------------------------------
CREATE TEMP TABLE seed_receipt ON COMMIT DROP AS
SELECT x.*, ('5eed0004-0006-4000-8000-0000000' || lpad(x.rn::text, 2, '0') || '00' || x.bucket)::uuid AS receipt_id,
       'RCPT-2026-' || lpad((row_number() OVER (ORDER BY x.paid_at))::text, 6, '0') AS receipt_number
FROM (
    SELECT a.rn, b.bucket,
           CASE WHEN a.plan = 'PARTIAL' AND b.bucket = 2 THEN 10000.00
                ELSE (SELECT sum(amount) FROM seed_ledger l WHERE l.rn = a.rn AND l.bucket = b.bucket) END::numeric(10,2) AS amount,
           CASE WHEN a.student_uid = 'STU-2020-00027' AND b.bucket = 2 THEN 'CHEQUE'
                WHEN a.student_uid = 'STU-2016-00011' AND b.bucket = 3 THEN 'CHEQUE'
                WHEN b.bucket = 3 THEN 'CASH'
                WHEN a.rn % 3 = 0 THEN 'UPI' WHEN a.rn % 3 = 1 THEN 'CASH' ELSE 'CHEQUE' END AS mode,
           CASE b.bucket
                WHEN 1 THEN TIMESTAMPTZ '2026-04-01 04:30:00+00' + ((a.rn % 9) || ' days')::interval + (a.rn * 7 || ' minutes')::interval
                WHEN 2 THEN TIMESTAMPTZ '2026-08-01 04:30:00+00' + ((a.rn % 9) || ' days')::interval + (a.rn * 7 || ' minutes')::interval
                ELSE CASE a.student_uid WHEN 'STU-2018-00012' THEN TIMESTAMPTZ '2026-10-03 06:10:00+00'
                                        WHEN 'STU-2016-00011' THEN TIMESTAMPTZ '2026-10-08 05:40:00+00'
                                        ELSE TIMESTAMPTZ '2026-10-15 07:25:00+00' END END AS paid_at,
           a.student_name, a.student_uid
    FROM seed_account a
    CROSS JOIN (VALUES (1), (2), (3)) AS b(bucket)
    WHERE (b.bucket = 1 AND a.plan <> 'NONE')
       OR (b.bucket = 2 AND a.plan IN ('TERM2', 'PARTIAL', 'BOUNCED', 'ALL'))
       OR (b.bucket = 3 AND a.plan = 'ALL')
) x;

INSERT INTO fee_payment_receipts (receipt_id, tenant_id, branch_id, account_id, receipt_number, total_amount_paid, payment_mode,
                                  instrument_number, bank_name, instrument_date, payer_name, remarks, gateway_transaction_id,
                                  payment_timestamp, collected_by, receipt_status, bounced_at, bounced_by, bounce_reason, bounce_charge,
                                  tally_synced, created_at, updated_at, created_by, updated_by, version)
SELECT r.receipt_id, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH',
       ('5eed0004-0004-4000-8000-0000000000' || lpad(r.rn::text, 2, '0'))::uuid, r.receipt_number, r.amount, r.mode,
       CASE r.mode WHEN 'CHEQUE' THEN CASE WHEN r.student_uid = 'STU-2020-00027' THEN '004512' ELSE lpad((100000 + r.rn * 37 + r.bucket)::text, 6, '0') END END,
       CASE r.mode WHEN 'CHEQUE' THEN CASE r.rn % 3 WHEN 0 THEN 'State Bank of India' WHEN 1 THEN 'HDFC Bank' ELSE 'ICICI Bank' END END,
       CASE r.mode WHEN 'CHEQUE' THEN (r.paid_at AT TIME ZONE 'Asia/Kolkata')::date END,
       'Parent of ' || r.student_name,
       CASE WHEN r.bucket = 3 THEN 'Term 3 paid in advance' END,
       CASE r.mode WHEN 'UPI' THEN 'UPI' || to_char(r.paid_at, 'YYYYMMDD') || lpad((r.rn * 1013 + r.bucket)::text, 8, '0') END,
       r.paid_at,
       CASE r.mode WHEN 'UPI' THEN 'payment-gateway' ELSE 'ramesh.accounts@dps.edu.in' END,
       CASE WHEN r.student_uid = 'STU-2020-00027' AND r.bucket = 2 THEN 'BOUNCED' ELSE 'ISSUED' END,
       CASE WHEN r.student_uid = 'STU-2020-00027' AND r.bucket = 2 THEN TIMESTAMPTZ '2026-10-05 06:00:00+00' END,
       CASE WHEN r.student_uid = 'STU-2020-00027' AND r.bucket = 2 THEN 'kavita.srinivasan@dps.edu.in' END,
       CASE WHEN r.student_uid = 'STU-2020-00027' AND r.bucket = 2 THEN 'Returned unpaid: insufficient funds' END,
       CASE WHEN r.student_uid = 'STU-2020-00027' AND r.bucket = 2 THEN 500.00 END,
       r.bucket < 3, r.paid_at, r.paid_at, 'seed:module04', NULL, 0
FROM seed_receipt r;

-- Allocations: each line of the bucket in full; Vihaan's 10,000 goes to Term 2 tuition. A bounced receipt keeps its
-- allocations but they no longer count as paid.
CREATE TEMP TABLE seed_alloc ON COMMIT DROP AS
SELECT ('5eed0004-0007-4000-' || lpad(l.rn::text, 4, '0') || '-0000000' || l.bucket || '00' || l.ll)::uuid AS allocation_id,
       r.receipt_id, l.ledger_id, r.paid_at,
       (CASE WHEN l.plan = 'PARTIAL' AND l.bucket = 2 THEN 10000 ELSE l.amount END)::numeric(10,2) AS amount,
       NOT (l.plan = 'BOUNCED' AND l.bucket = 2) AS counts
FROM seed_ledger l
JOIN seed_receipt r ON r.rn = l.rn AND r.bucket = l.bucket
WHERE NOT (l.plan = 'PARTIAL' AND l.bucket = 2 AND l.ll <> '02');

-- Ledger lines: paid = allocations of issued receipts; Kabir's bounce charge goes on his earliest bounced line (Lab, 15 Jul)
INSERT INTO student_fee_ledgers (ledger_id, tenant_id, account_id, schedule_id, schedule_line_id, fee_head_id, installment_label, due_date,
                                 base_amount, concession_amount, late_fine_amount, paid_amount, payment_status,
                                 created_at, updated_at, created_by, updated_by, version)
SELECT l.ledger_id, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', l.account_id, l.schedule_id, l.line_id, l.fee_head_id, l.label, l.due_date,
       l.amount, 0, l.fine, l.paid,
       CASE WHEN l.amount + l.fine - l.paid <= 0 THEN 'PAID'
            WHEN l.due_date < CURRENT_DATE THEN 'OVERDUE'
            WHEN l.paid > 0 THEN 'PARTIALLY_PAID'
            ELSE 'PENDING' END,
       TIMESTAMPTZ '2026-03-20 05:00:00+00', TIMESTAMPTZ '2026-03-20 05:00:00+00', 'seed:module04', NULL, 0
FROM (
    SELECT l.*,
           COALESCE((SELECT sum(x.amount) FROM seed_alloc x WHERE x.ledger_id = l.ledger_id AND x.counts), 0)::numeric(10,2) AS paid,
           (CASE WHEN l.plan = 'BOUNCED' AND l.ll = '05' THEN 500 ELSE 0 END)::numeric(10,2) AS fine
    FROM seed_ledger l
) l;

INSERT INTO fee_receipt_allocations (allocation_id, receipt_id, ledger_id, amount, created_at, updated_at, created_by, updated_by, version)
SELECT allocation_id, receipt_id, ledger_id, amount, paid_at, paid_at, 'seed:module04', NULL, 0 FROM seed_alloc;

COMMIT;
