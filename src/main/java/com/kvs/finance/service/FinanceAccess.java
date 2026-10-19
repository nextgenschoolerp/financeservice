package com.kvs.finance.service;

import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.util.SecurityUtils;
import com.kvs.finance.entity.FeeAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Tenant and parent scoping on top of the role checks. Records of another institution look as if they did not exist;
 * a parent (APPLICANT_PARENT without a staff role) only sees fee accounts linked to their own user id.
 */
@Component
public class FinanceAccess {

    private static final Set<String> STAFF_ROLES = Set.of("ROLE_CHIEF_ACCOUNTANT", "ROLE_FINANCE_CONTROLLER", "ROLE_ACCOUNTS_CLERK",
            "ROLE_FINANCE_CASHIER", "ROLE_FINANCE_AUDITOR", "ROLE_TRUST_TREASURER", "ROLE_FINANCE_OFFICER",
            "ROLE_SCHOOL_PRINCIPAL", "ROLE_REGISTRAR", "ROLE_SUPER_ADMIN");

    @Autowired
    private TenantAccessGuard tenantGuard;

    /** The tenant a request works on: the one it names (if the caller may use it) or the caller's own. */
    public String tenantFor(String requestedTenantId) {
        if (requestedTenantId != null && !requestedTenantId.isBlank()) {
            tenantGuard.requireTenant(requestedTenantId);
            return requestedTenantId;
        }
        return tenantGuard.currentTenantId()
                .orElseThrow(() -> new BadRequestException("tenantId is required (the token carries no institution)"));
    }

    /** A missing required query parameter answers 400 (not the generic 500 of MissingServletRequestParameterException). */
    public static String requireBranch(String branchId) {
        if (branchId == null || branchId.isBlank()) {
            throw new BadRequestException("branchId is required");
        }
        return branchId;
    }

    public void requireTenantRecord(String tenantId, String resource, String field, Object value) {
        tenantGuard.requireTenantRecord(tenantId, resource, field, value);
    }

    /** True for a parent account without any finance or school staff role. */
    public boolean isParentOnly() {
        List<String> roles = SecurityUtils.getCurrentUserRoles();
        return roles.contains("ROLE_APPLICANT_PARENT") && roles.stream().noneMatch(STAFF_ROLES::contains);
    }

    /** Hides another tenant's account, and, from a parent, any account not linked to them. */
    public void requireAccount(FeeAccount account) {
        tenantGuard.requireTenantRecord(account.getTenantId(), "FeeAccount", "studentId", account.getStudentId());
        if (isParentOnly() && !tenantGuard.currentUserId().map(id -> id.equals(account.getParentAccountId())).orElse(false)) {
            throw new ResourceNotFoundException("FeeAccount", "studentId", account.getStudentId());
        }
    }

    public boolean canSee(FeeAccount account) {
        try {
            requireAccount(account);
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    public String currentUser() {
        return SecurityUtils.getCurrentUserEmail().orElse("system");
    }
}
