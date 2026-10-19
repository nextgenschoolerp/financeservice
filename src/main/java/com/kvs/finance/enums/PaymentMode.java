package com.kvs.finance.enums;

/** How a fee payment was made. The counter (US-04013) takes CASH, CHEQUE and DEMAND_DRAFT; the others come from the payment gateway. */
public enum PaymentMode {
    CASH, CHEQUE, DEMAND_DRAFT, UPI, CARD, NET_BANKING
}
