// ============================================================
// MongoDB initialisation script
// Finance Learning Project — Notification Service
// ============================================================

// Switch to the notifications database
db = db.getSiblingDB('notifications_db');

// Create a dedicated user for the application
db.createUser({
    user: 'finance_user',
    pwd: 'finance_pass',
    roles: [{ role: 'readWrite', db: 'notifications_db' }]
});

// ── notifications collection ────────────────────────────────────────────────
db.createCollection('notifications', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['eventType', 'accountId', 'message', 'createdAt', 'status'],
            properties: {
                eventType: {
                    bsonType: 'string',
                    description: 'Type of event (e.g. ACCOUNT_CREATED, TRANSACTION_COMPLETED)'
                },
                accountId: {
                    bsonType: 'long',
                    description: 'Related account ID'
                },
                message: {
                    bsonType: 'string',
                    description: 'Human-readable notification message'
                },
                status: {
                    bsonType: 'string',
                    enum: ['UNREAD', 'READ', 'ARCHIVED'],
                    description: 'Notification read status'
                },
                createdAt: {
                    bsonType: 'date',
                    description: 'Timestamp the notification was created'
                }
            }
        }
    }
});

db.notifications.createIndex({ accountId: 1 });
db.notifications.createIndex({ status: 1 });
db.notifications.createIndex({ createdAt: -1 });
db.notifications.createIndex({ eventType: 1, accountId: 1 });

// ── audit_logs collection ───────────────────────────────────────────────────
db.createCollection('audit_logs', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['service', 'action', 'entityType', 'entityId', 'timestamp'],
            properties: {
                service: { bsonType: 'string' },
                action: { bsonType: 'string' },
                entityType: { bsonType: 'string' },
                entityId: { bsonType: 'string' },
                payload: { bsonType: 'object' },
                timestamp: { bsonType: 'date' }
            }
        }
    }
});

db.audit_logs.createIndex({ service: 1, action: 1 });
db.audit_logs.createIndex({ entityType: 1, entityId: 1 });
db.audit_logs.createIndex({ timestamp: -1 });

print('MongoDB initialisation complete: notifications_db created with collections and indexes.');
