package ru.aritmos.integration.audit;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Базовый аудит для ключевых операций этапа 1.
 */
@Singleton
public class AuditService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    public void auditSuccess(String action, String subject, String target) {
        LOG.info("AUDIT_SUCCESS action={} subject={} target={}", action, subject, target);
    }

    public void auditDenied(String action, String subject) {
        LOG.warn("AUDIT_DENIED action={} subject={}", action, subject);
    }
}
