const express = require('express');
const config = require('./config');
const { gradingQueue, redisConnection, addGradingJob } = require('./queue');
const { createWorker } = require('./worker');

const app = express();
app.use(express.json());

// Start background BullMQ worker
let worker = null;
if (process.env.WORKER_ENABLED !== 'false') {
  worker = createWorker();
  console.log(`[Server] BullMQ worker initialized with concurrency ${config.queue.concurrency}`);
}

// Health check endpoint
app.get('/api/health', async (req, res) => {
  try {
    const ping = await redisConnection.ping();
    const counts = await gradingQueue.getJobCounts('waiting', 'active', 'completed', 'failed', 'delayed');
    res.json({
      status: 'UP',
      redis: ping === 'PONG' ? 'CONNECTED' : ping,
      queue: config.queue.name,
      counts,
    });
  } catch (err) {
    res.status(503).json({
      status: 'DOWN',
      error: err.message,
    });
  }
});

// Enqueue endpoint called by Spring Boot
app.post('/api/jobs/grade', async (req, res) => {
  const { submissionId, examId, submissionType } = req.body;

  if (!submissionId) {
    return res.status(400).json({ error: 'submissionId is required' });
  }

  try {
    const job = await addGradingJob({ submissionId, examId, submissionType });
    res.status(201).json({
      jobId: job.id,
      name: job.name,
      status: 'enqueued',
      submissionId,
    });
  } catch (err) {
    console.error('[Server] Failed to enqueue job:', err.message);
    res.status(500).json({ error: 'Failed to enqueue job: ' + err.message });
  }
});

// Query job status
app.get('/api/jobs/:jobId', async (req, res) => {
  try {
    const job = await gradingQueue.getJob(req.params.jobId);
    if (!job) {
      return res.status(404).json({ error: 'Job not found' });
    }

    const state = await job.getState();
    res.json({
      id: job.id,
      name: job.name,
      state,
      data: job.data,
      attemptsMade: job.attemptsMade,
      failedReason: job.failedReason,
      returnvalue: job.returnvalue,
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Start HTTP listener
const server = app.listen(config.port, () => {
  console.log(`[Server] BullMQ producer & health server listening on port ${config.port}`);
});

// Graceful shutdown
async function shutdown(signal) {
  console.log(`[Server] Received ${signal}, closing server and worker...`);
  server.close();
  if (worker) {
    await worker.close();
  }
  await gradingQueue.close();
  await redisConnection.quit();
  console.log('[Server] Gracefully stopped');
  process.exit(0);
}

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));

module.exports = { app, server };
