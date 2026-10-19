package com.kvs.finance.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_ip_address", columnList = "ip_address"),
        @Index(name = "idx_audit_event_time", columnList = "event_time")
})
@Getter
@Setter
public class AuditLog extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "action", nullable = false, length = 200)
    private String action;

    @Column(name = "resource", length = 200)
    private String resource;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "details", length = 1000)
    private String details;
}
