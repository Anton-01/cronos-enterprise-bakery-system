-- Permissions of auth-quotes-calendar-notifications §10. Rows exist before the startup catalog sync so the role
-- grants below can reference them; SUPER_ADMIN holds every permission implicitly.
INSERT INTO permissions (name, description, module, resource, action, risk, deprecated, created_at, created_by) VALUES
    ('IAM.REGISTRATION.READ', 'IAM.REGISTRATION.READ', 'IAM', 'REGISTRATION', 'READ', 'LOW', FALSE, now(), 'FLYWAY_V25'),
    ('IAM.REGISTRATION.DECIDE', 'IAM.REGISTRATION.DECIDE', 'IAM', 'REGISTRATION', 'DECIDE', 'HIGH', FALSE, now(), 'FLYWAY_V25'),
    ('CALENDAR.EVENT.READ', 'CALENDAR.EVENT.READ', 'CALENDAR', 'EVENT', 'READ', 'LOW', FALSE, now(), 'FLYWAY_V25'),
    ('CALENDAR.EVENT.MANAGE', 'CALENDAR.EVENT.MANAGE', 'CALENDAR', 'EVENT', 'MANAGE', 'LOW', FALSE, now(), 'FLYWAY_V25'),
    ('NOTIFICATION.EMAIL_TEMPLATE.READ', 'NOTIFICATION.EMAIL_TEMPLATE.READ', 'NOTIFICATION', 'EMAIL_TEMPLATE', 'READ', 'LOW', FALSE, now(), 'FLYWAY_V25'),
    ('NOTIFICATION.EMAIL_TEMPLATE.TEST_SEND', 'NOTIFICATION.EMAIL_TEMPLATE.TEST_SEND', 'NOTIFICATION', 'EMAIL_TEMPLATE', 'TEST_SEND', 'MEDIUM', FALSE, now(), 'FLYWAY_V25'),
    ('NOTIFICATION.BROADCAST.SEND', 'NOTIFICATION.BROADCAST.SEND', 'NOTIFICATION', 'BROADCAST', 'SEND', 'HIGH', FALSE, now(), 'FLYWAY_V25')
ON CONFLICT (name) DO NOTHING;

-- Registration review and the email catalog: platform administrators.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN'
  AND p.name IN ('IAM.REGISTRATION.READ', 'IAM.REGISTRATION.DECIDE',
                 'NOTIFICATION.EMAIL_TEMPLATE.READ', 'NOTIFICATION.EMAIL_TEMPLATE.TEST_SEND')
ON CONFLICT DO NOTHING;

-- The calendar belongs with quotes: everyone who works with quotes keeps their own calendar.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name IN ('CALENDAR.EVENT.READ', 'CALENDAR.EVENT.MANAGE')
  AND (r.code IN ('ADMIN', 'MANAGER', 'USER')
       OR r.id IN (SELECT rp.role_id FROM role_permissions rp JOIN permissions x ON x.id = rp.permission_id
                   WHERE x.name = 'QUOTE.QUOTE.READ'))
ON CONFLICT DO NOTHING;

-- Tokens carry permissions: force a refresh for everyone whose access just changed.
UPDATE users SET access_version = access_version + 1
WHERE id IN (SELECT ur.user_id FROM user_roles ur JOIN role_permissions rp ON rp.role_id = ur.role_id
             JOIN permissions p ON p.id = rp.permission_id
             WHERE p.name LIKE 'CALENDAR.%' OR p.name LIKE 'IAM.REGISTRATION.%' OR p.name LIKE 'NOTIFICATION.%');
