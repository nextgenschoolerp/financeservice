package com.kvs.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** US-04018: map a fee head to an accounting ledger code. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLedgerMappingRequest {

    @NotBlank(message = "ledgerCode is required")
    @Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{1,29}$", message = "ledgerCode must be 2-30 upper-case letters, digits or hyphens, e.g. 4100-TUITION")
    private String ledgerCode;

    @NotBlank(message = "ledgerName is required")
    @Size(max = 100, message = "ledgerName must be at most 100 characters")
    private String ledgerName;
}
