package com.kvs.finance.controller;

import com.kvs.finance.dto.AccessRuleResponse;
import com.kvs.core.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lists every financeservice endpoint with the roles its {@code @PreAuthorize} allows, read from the running service.
 * Lets tools (e.g. the student lifecycle workflow runner) verify that a school's staff hold the roles a workflow needs
 * without duplicating the rules. Same contract as admissionservice's {@code GET /api/v1/admissions/access-rules}.
 */
@RestController
@RequestMapping("/api/v1/finance/access-rules")
public class AccessRuleController {

    private static final Pattern ROLE = Pattern.compile("'(?:ROLE_)?([A-Z_]+)'");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AccessRuleResponse>>> list() {
        List<AccessRuleResponse> rules = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = e.getValue();
            if (!handler.getBeanType().getPackageName().startsWith("com.kvs.finance")) {
                continue;
            }
            PreAuthorize rule = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
            if (rule == null) {
                rule = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
            }
            Set<String> roles = new LinkedHashSet<>();
            if (rule != null) {
                Matcher m = ROLE.matcher(rule.value());
                while (m.find()) {
                    roles.add(m.group(1));
                }
            }
            Set<String> paths = e.getKey().getPatternValues();
            Set<String> methods = e.getKey().getMethodsCondition().getMethods().stream().map(Enum::name)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            for (String path : paths) {
                for (String method : methods.isEmpty() ? Set.of("ANY") : methods) {
                    rules.add(AccessRuleResponse.builder()
                            .method(method)
                            .path(path)
                            .roles(List.copyOf(roles))
                            .handler(handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName())
                            .build());
                }
            }
        }
        rules.sort(Comparator.comparing(AccessRuleResponse::getPath).thenComparing(AccessRuleResponse::getMethod));
        return ResponseEntity.ok(ApiResponse.success(rules));
    }
}
