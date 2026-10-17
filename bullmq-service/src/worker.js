const { Worker, UnrecoverableError } = require('bullmq');
const axios = require('axios');
const config = require('./config');
const { redisConnection } = require('./queue');

function createWorker() {
  const worker = new Worker(
    config.queue.name,
    async (job) => {
      const { submissionId, examId, submissionType } = job.data;
      console.log(`[Worker] Processing job ${job.id} for submission ${submissionId} (attempt ${job.attemptsMade + 1}/${config.queue.maxAttempts})`);

      try {
        const response = await axios.post(
          `${config.backend.url}/api/internal/submissions/${submissionId}/process-grading`,
          {},
          {
            headers: {
              'X-Internal-Token': config.backend.internalSecret,
              'Content-Type': 'application/json',
            },
            timeout: config.backend.timeoutMs,
          }
        );

        console.log(`[Worker] Successfully completed job ${job.id} for submission ${submissionId}:`, response.data);
        return response.data;

      } catch (err) {
        if (err.response) {
          const status = err.response.status;
          const errorMsg = err.response.data?.error || err.message;
          console.error(`[Worker] Backend rejected job ${job.id} with HTTP ${status}: ${errorMsg}`);

          // 400, 401, 403, 404: Non-retryable client/auth errors
          if (status === 400 || status === 401 || status === 403 || status === 404) {
            throw new UnrecoverableError(`Non-retryable backend error (${status}): ${errorMsg}`);
          }

          // 5xx or network errors: retryable with backoff
          throw new Error(`Backend processing error (${status}): ${errorMsg}`);
        } else {
          // Network connectivity / timeout
          console.error(`[Worker] Network error connecting to backend for job ${job.id}:`, err.message);
          throw new Error(`Network failure calling backend: ${err.message}`);
        }
      }
    },
    {
      connection: redisConnection,
      concurrency: config.queue.concurrency,
    }
  );

  worker.on('completed', (job, returnvalue) => {
    console.log(`[Worker] Job ${job.id} completed successfully`);
  });

  worker.on('failed', (job, err) => {
    console.error(`[Worker] Job ${job?.id} failed with reason: ${err.message}`);
  });

  worker.on('error', (err) => {
    console.error('[Worker] Worker encountered error:', err.message);
  });

  return worker;
}

module.exports = {
  createWorker,
};
