package com.kvs.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** US-04019: mark a cheque or demand draft receipt as bounced. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BounceChequeRequest {

    @NotBlank(message = "reason is required")
    @Size(max = 500, message = "reason must be at most 500 characters")
    private String reason;

    /** Bank / handling charge added to the student's dues (optional, default 0) */
    @DecimalMin(value = "0.00", message = "bounceCharge must not be negative")
    @Digits(integer = 6, fraction = 2, message = "bounceCharge must have at most 6 digits and 2 decimals")
    private BigDecimal bounceCharge;
}
