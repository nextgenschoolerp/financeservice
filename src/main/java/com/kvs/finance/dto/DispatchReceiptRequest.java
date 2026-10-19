package com.kvs.finance.dto;

import com.kvs.finance.enums.DispatchChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** US-04004: send a receipt to a parent. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchReceiptRequest {

    @NotNull(message = "channel is required")
    private DispatchChannel channel;

    /** Mobile number (+919876543210) for WHATSAPP / SMS, e-mail address for EMAIL */
    @NotBlank(message = "recipient is required")
    @Size(max = 150, message = "recipient must be at most 150 characters")
    private String recipient;
}
