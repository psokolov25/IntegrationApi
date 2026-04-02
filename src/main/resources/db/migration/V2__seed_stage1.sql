INSERT INTO audit_event(action, subject, target, status)
VALUES ('bootstrap', 'system', 'integration-api', 'SUCCESS')
ON CONFLICT DO NOTHING;
