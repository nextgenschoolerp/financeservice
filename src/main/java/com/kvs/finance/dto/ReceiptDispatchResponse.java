package com.kvs.finance.dto;

import com.kvs.finance.enums.DispatchChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A queued receipt dispatch. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptDispatchResponse {

    private UUID dispatchId;
    private UUID receiptId;
    private String receiptNumber;
    private DispatchChannel channel;
    private String recipient;
    private String status;
    /** why it was not sent, or which mailbox sent it */
    private String statusDetail;
    private String requestedBy;
    private Instant requestedAt;
}
