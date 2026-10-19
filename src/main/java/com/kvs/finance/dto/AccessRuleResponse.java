package com.kvs.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** One financeservice endpoint and the roles its @PreAuthorize allows. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccessRuleResponse {
    private String method;
    /** Full path pattern, e.g. /api/v1/finance/… */
    private String path;
    /** Role codes without the ROLE_ prefix; empty when the endpoint only requires authentication. */
    private List<String> roles;
    private String handler;
}
