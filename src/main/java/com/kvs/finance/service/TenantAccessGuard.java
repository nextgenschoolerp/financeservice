package com.kvs.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kvs.core.exception.ForbiddenException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Tenant isolation for fee records. authservice tokens carry the caller's institution in a {@code tenantId}
 * claim; a caller may only see fee records of that tenant, except a SUPER_ADMIN, who may cross tenants.
 * A token without the claim matches no tenant.
 */
@Slf4j
@Component
public class TenantAccessGuard {

    @Autowired
    private ObjectMapper objectMapper;

    /** True when the caller is a super administrator. */
    /**
     * A campus check: a caller whose token names a branch may only read that branch. Super administrators and trustees
     * (who oversee every campus of the school) are exempt. A token without a branch claim is not limited here.
     */
    public void requireBranch(String branchId) {
        if (branchId == null || isSuperAdmin() || SecurityUtils.getCurrentUserRoles().stream().anyMatch(r -> r.equals("ROLE_TRUSTEE") || r.equals("ROLE_TRUST_TREASURER")
                )) {
            return;
        }
        boolean otherCampus = claim("branchId").map(mine -> !mine.equalsIgnoreCase(branchId)).orElse(false);
        if (otherCampus) {
            throw new ForbiddenException("You may only access records of your own campus");
        }
    }

    public boolean isSuperAdmin() {
        return SecurityUtils.getCurrentUserRoles().contains("ROLE_SUPER_ADMIN");
    }

    /** The caller's tenant from the bearer token, if the token carries one. */
    public Optional<String> currentTenantId() {
        return claim("tenantId");
    }

    /** The caller's authservice user id (userId claim), used to match a parent to their children's fee accounts. */
    public Optional<String> currentUserId() {
        return claim("userId");
    }

    /** True when the caller may act on records of the given tenant. */
    public boolean canAccessTenant(String tenantId) {
        return isSuperAdmin() || (tenantId != null && currentTenantId()
                .map(tenantId::equalsIgnoreCase)
                .orElse(false));
    }

    /**
     * Hides a record of another tenant as if it did not exist.
     *
     * @throws ResourceNotFoundException when the caller may not access the record's tenant
     */
    public void requireTenantRecord(String tenantId, String resourceName, String fieldName, Object fieldValue) {
        if (!canAccessTenant(tenantId)) {
            throw new ResourceNotFoundException(resourceName, fieldName, fieldValue);
        }
    }

    /**
     * Rejects a request that names another tenant explicitly (e.g. a {@code tenantId} query parameter).
     *
     * @throws ForbiddenException when the caller may not access that tenant
     */
    public void requireTenant(String tenantId) {
        if (!canAccessTenant(tenantId)) {
            throw new ForbiddenException("You may only access fee records of your own institution");
        }
    }

    private Optional<String> claim(String name) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        HttpServletRequest request = attributes.getRequest();
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        // The JwtAuthenticationFilter has already validated this token's signature and expiry; only its payload is read here
        String[] parts = header.substring(7).split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            JsonNode payload = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            return Optional.ofNullable(payload.path(name).textValue()).filter(v -> !v.isBlank());
        } catch (Exception e) {
            log.debug("No {} claim in the bearer token: {}", name, e.getMessage());
            return Optional.empty();
        }
    }
}
