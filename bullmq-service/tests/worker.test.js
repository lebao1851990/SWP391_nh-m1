process.env.INTERNAL_API_SECRET = 'test-internal-secret-for-bullmq-worker-suite';

const { test, describe, before, after } = require('node:test');
const assert = require('node:assert');
const http = require('node:http');
const { Queue, Worker } = require('bullmq');
const IORedis = require('ioredis');
const axios = require('axios');
const config = require('../src/config');

const TEST_SECRET = 'test-internal-secret-for-bullmq-worker-suite';

describe('BullMQ Worker Execution & Backend Bridge Tests', () => {
  let mockServer;
  let receivedHeaders = {};
  let receivedSubmissionId = '';
  let serverPort = 8099;
  let testQueue;
  let testWorker;
  let testRedis;

  before(async () => {
    testRedis = new IORedis(config.redis);
    testQueue = new Queue('test-grading-queue', { connection: testRedis });

    // Mock Spring Boot backend internal API server
    mockServer = http.createServer((req, res) => {
      receivedHeaders = req.headers;
      receivedSubmissionId = req.url.split('/')[4];

      if (req.headers['x-internal-token'] !== TEST_SECRET) {
        res.writeHead(401, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Unauthorized: invalid or missing internal token' }));
        return;
      }

      if (req.url.includes('sub-error')) {
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Internal failure' }));
        return;
      }

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        submissionId: receivedSubmissionId,
        status: 'COMPLETED',
        executionStatus: 'SUCCESS',
        exitCode: 0,
        message: 'Graded successfully'
      }));
    });

    await new Promise((resolve) => mockServer.listen(serverPort, resolve));
  });

  after(async () => {
    if (testWorker) await testWorker.close();
    await testQueue.close();
    await testRedis.quit();
    await new Promise((resolve) => mockServer.close(resolve));
  });

  test('Worker successfully processes job and sends configured X-Internal-Token header', async () => {
    let jobProcessed = false;

    testWorker = new Worker(
      'test-grading-queue',
      async (job) => {
        const response = await axios.post(
          `http://localhost:${serverPort}/api/internal/submissions/${job.data.submissionId}/process-grading`,
          {},
          {
            headers: {
              'X-Internal-Token': config.backend.internalSecret,
            },
          }
        );
        jobProcessed = true;
        return response.data;
      },
      { connection: testRedis }
    );

    const job = await testQueue.add('test-grade', {
      submissionId: 'sub-verify-101',
      examId: 'exam-verify-202',
    });

    // Wait for worker to finish
    await new Promise((resolve) => {
      testWorker.on('completed', (j) => {
        if (j.id === job.id) resolve();
      });
    });

    assert.strictEqual(jobProcessed, true);
    assert.strictEqual(receivedHeaders['x-internal-token'], TEST_SECRET);
    assert.strictEqual(receivedSubmissionId, 'sub-verify-101');
  });

  test('Worker call is rejected with 401 when token does not match', async () => {
    let errorStatus = null;
    try {
      await axios.post(
        `http://localhost:${serverPort}/api/internal/submissions/sub-verify-101/process-grading`,
        {},
        {
          headers: {
            'X-Internal-Token': 'wrong-unauthorized-secret',
          },
        }
      );
    } catch (err) {
      errorStatus = err.response ? err.response.status : null;
    }
    assert.strictEqual(errorStatus, 401);
  });
});
