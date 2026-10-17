const { Queue } = require('bullmq');
const IORedis = require('ioredis');
const config = require('./config');

const redisConnection = new IORedis(config.redis);

redisConnection.on('error', (err) => {
  console.error('[BullMQ Redis] Connection error:', err.message);
});

redisConnection.on('connect', () => {
  console.log('[BullMQ Redis] Connected to Redis at', `${config.redis.host}:${config.redis.port}`);
});

const gradingQueue = new Queue(config.queue.name, {
  connection: redisConnection,
  defaultJobOptions: {
    attempts: config.queue.maxAttempts,
    backoff: {
      type: 'exponential',
      delay: config.queue.backoffDelayMs,
    },
    removeOnComplete: 100,
    removeOnFail: 200,
  },
});

/**
 * Enqueues a grading job with stable job ID and idempotency.
 *
 * @param {Object} data - { submissionId, examId, submissionType }
 * @returns {Promise<Job>}
 */
async function addGradingJob(data) {
  if (!data || !data.submissionId) {
    throw new Error('submissionId is required');
  }

  const jobId = `grading-${data.submissionId}`;
  const job = await gradingQueue.add('grade-submission', data, {
    jobId,
  });

  return job;
}

module.exports = {
  gradingQueue,
  redisConnection,
  addGradingJob,
};
