require('dotenv').config();

module.exports = {
  port: parseInt(process.env.PORT || '3001', 10),
  redis: {
    host: process.env.REDIS_HOST || 'localhost',
    port: parseInt(process.env.REDIS_PORT || '6379', 10),
    password: process.env.REDIS_PASSWORD || undefined,
    maxRetriesPerRequest: null, // Required by BullMQ
  },
  backend: {
    url: (process.env.BACKEND_URL || 'http://localhost:8080').replace(/\/$/, ''),
    internalSecret: process.env.INTERNAL_API_SECRET || 'codeassess-internal-secret-2026',
    timeoutMs: parseInt(process.env.BACKEND_TIMEOUT_MS || '30000', 10),
  },
  queue: {
    name: process.env.QUEUE_NAME || 'grading-queue',
    concurrency: parseInt(process.env.BULLMQ_CONCURRENCY || '2', 10),
    maxAttempts: parseInt(process.env.BULLMQ_MAX_ATTEMPTS || '3', 10),
    backoffDelayMs: parseInt(process.env.BULLMQ_BACKOFF_DELAY_MS || '2000', 10),
  },
};
