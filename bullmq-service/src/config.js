require('dotenv').config();

const LEGACY_DEFAULT_SECRET = 'codeassess-internal-secret-2026';

/**
 * Validates the internal API secret required for service-to-service communication.
 * Enforces fail-fast behavior if missing, blank, or set to the known insecure legacy default.
 *
 * @param {string|undefined} secret
 * @returns {string} Trimmed valid secret
 * @throws {Error} If secret is invalid or insecure
 */
function validateInternalSecret(secret) {
  if (!secret || typeof secret !== 'string' || secret.trim() === '') {
    throw new Error(
      'CRITICAL SECURITY CONFIGURATION ERROR: INTERNAL_API_SECRET is missing or empty. ' +
      'A non-blank secret is required for BullMQ worker to authenticate with the backend.'
    );
  }
  if (secret.trim().toLowerCase() === LEGACY_DEFAULT_SECRET.toLowerCase()) {
    throw new Error(
      'CRITICAL SECURITY CONFIGURATION ERROR: INTERNAL_API_SECRET cannot use the insecure legacy default value. ' +
      'Configure a secure, random internal secret.'
    );
  }
  return secret.trim();
}

const internalSecret = validateInternalSecret(process.env.INTERNAL_API_SECRET);

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
    internalSecret,
    timeoutMs: parseInt(process.env.BACKEND_TIMEOUT_MS || '30000', 10),
  },
  queue: {
    name: process.env.QUEUE_NAME || 'grading-queue',
    concurrency: parseInt(process.env.BULLMQ_CONCURRENCY || '2', 10),
    maxAttempts: parseInt(process.env.BULLMQ_MAX_ATTEMPTS || '3', 10),
    backoffDelayMs: parseInt(process.env.BULLMQ_BACKOFF_DELAY_MS || '2000', 10),
  },
  // Exported for targeted configuration test isolation
  _validateInternalSecret: validateInternalSecret,
  _LEGACY_DEFAULT_SECRET: LEGACY_DEFAULT_SECRET,
};
