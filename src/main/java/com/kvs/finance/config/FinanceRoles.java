package com.kvs.finance.config;

/**
 * Who may call what (@PreAuthorize expressions). Module 04 actors → roles:
 * Chief Accountant → CHIEF_ACCOUNTANT, Finance Controller → FINANCE_CONTROLLER, Accounts Clerk → ACCOUNTS_CLERK,
 * Fee Cashier → FINANCE_CASHIER, Auditor → FINANCE_AUDITOR, Trust Treasurer → TRUST_TREASURER,
 * School Principal → SCHOOL_PRINCIPAL, Parent → APPLICANT_PARENT (own children only).
 */
public final class FinanceRoles {

    private FinanceRoles() {
    }

    private static final String STAFF = "'CHIEF_ACCOUNTANT', 'FINANCE_CONTROLLER', 'ACCOUNTS_CLERK', 'FINANCE_CASHIER', "
            + "'FINANCE_AUDITOR', 'TRUST_TREASURER', 'FINANCE_OFFICER', 'SCHOOL_PRINCIPAL', 'SUPER_ADMIN'";

    /** Finance staff, the principal and super administrators: lists, reports and receipts of a branch */
    public static final String FINANCE_STAFF = "hasAnyRole(" + STAFF + ")";

    /** US-04000: create fee heads and schedules, post demands */
    public static final String CONFIGURE_FEES = "hasAnyRole('CHIEF_ACCOUNTANT', 'FINANCE_CONTROLLER', 'SUPER_ADMIN')";

    /** US-04000: read fee heads and schedules (parents see the fee structure of their child's class) */
    public static final String READ_FEE_STRUCTURE = "hasAnyRole(" + STAFF + ", 'REGISTRAR', 'APPLICANT_PARENT')";

    /** US-04018: map fee heads to accounting ledger codes */
    public static final String MAP_LEDGERS = "hasAnyRole('FINANCE_CONTROLLER', 'ACCOUNTS_CLERK', 'CHIEF_ACCOUNTANT', 'SUPER_ADMIN')";

    /** A student's fee account and receipts (the registrar sees them on the student profile) */
    public static final String VIEW_STUDENT_FEES = "hasAnyRole(" + STAFF + ", 'REGISTRAR', 'APPLICANT_PARENT')";

    /** US-04013: take cash, cheque and demand draft payments at the counter */
    public static final String COLLECT_AT_COUNTER = "hasAnyRole('FINANCE_CASHIER', 'TRUST_TREASURER', 'CHIEF_ACCOUNTANT', 'SUPER_ADMIN')";

    /** US-04004: send a receipt over WhatsApp / SMS / e-mail */
    public static final String DISPATCH_RECEIPTS = "hasAnyRole('FINANCE_CASHIER', 'CHIEF_ACCOUNTANT', 'ACCOUNTS_CLERK', 'APPLICANT_PARENT', 'SUPER_ADMIN')";

    /** US-04005: run the late fee accrual */
    public static final String ACCRUE_LATE_FEES = "hasAnyRole('TRUST_TREASURER', 'FINANCE_CASHIER', 'CHIEF_ACCOUNTANT', 'SUPER_ADMIN')";

    /** US-04019: mark a cheque as bounced and reverse its credit */
    public static final String BOUNCE_CHEQUES = "hasAnyRole('FINANCE_AUDITOR', 'SCHOOL_PRINCIPAL', 'CHIEF_ACCOUNTANT', 'SUPER_ADMIN')";
}
