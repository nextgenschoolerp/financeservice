package com.kvs.finance.enums;

/** Status of one fee demand (installment) in a student's ledger, as in module4.sql payment_status_enum. */
public enum PaymentStatus {
    PENDING, PAID, PARTIALLY_PAID, OVERDUE, CANCELLED
}
