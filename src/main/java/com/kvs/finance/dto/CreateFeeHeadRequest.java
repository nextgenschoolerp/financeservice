package com.kvs.finance.dto;

import com.kvs.finance.enums.FeeHeadCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** US-04000: a new fee head such as Tuition, Lab or Transport. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFeeHeadRequest {

    /** Optional; defaults to the caller's institution (tenantId claim). A super administrator without one must send it. */
    private String tenantId;

    @NotBlank(message = "headCode is required")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$", message = "headCode must be 2-30 upper-case letters, digits or underscores, e.g. TUITION")
    private String headCode;

    @NotBlank(message = "headName is required")
    @Size(max = 100, message = "headName must be at most 100 characters")
    private String headName;

    @NotNull(message = "category is required")
    private FeeHeadCategory category;

    /** Billed every year (default true); false for one-time heads such as the admission fee. */
    private Boolean recurring;

    /** US-04018: accounting ledger code, e.g. 4100-TUITION (optional here, can be mapped later). */
    @Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{1,29}$", message = "ledgerCode must be 2-30 upper-case letters, digits or hyphens, e.g. 4100-TUITION")
    private String ledgerCode;

    @Size(max = 100, message = "ledgerName must be at most 100 characters")
    private String ledgerName;
}
