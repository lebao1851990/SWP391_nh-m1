const { test, describe, before, after } = require('node:test');
const assert = require('node:assert');
const { gradingQueue, addGradingJob, redisConnection } = require('../src/queue');

describe('BullMQ Queue & Job Lifecycle Tests', () => {
  before(async () => {
    // Clean queue before testing
    await gradingQueue.drain();
  });

  after(async () => {
    await gradingQueue.close();
    await redisConnection.quit();
  });

  test('addGradingJob requires submissionId', async () => {
    await assert.rejects(
      async () => {
        await addGradingJob({});
      },
      /submissionId is required/
    );
  });

  test('addGradingJob enqueues job with stable ID format', async () => {
    const job = await addGradingJob({
      submissionId: 'sub-test-123',
      examId: 'exam-test-456',
      submissionType: 'SINGLE_FILE',
    });

    assert.strictEqual(job.id, 'grading-sub-test-123');
    assert.strictEqual(job.data.submissionId, 'sub-test-123');
    assert.strictEqual(job.data.examId, 'exam-test-456');

    // Retrieve from queue
    const fetchedJob = await gradingQueue.getJob('grading-sub-test-123');
    assert.ok(fetchedJob);
    assert.strictEqual(fetchedJob.id, 'grading-sub-test-123');
  });

  test('addGradingJob deduplicates identical job IDs', async () => {
    const job1 = await addGradingJob({
      submissionId: 'sub-dup-001',
      examId: 'exam-001',
      submissionType: 'SINGLE_FILE',
    });

    const job2 = await addGradingJob({
      submissionId: 'sub-dup-001',
      examId: 'exam-001',
      submissionType: 'SINGLE_FILE',
    });

    assert.strictEqual(job1.id, 'grading-sub-dup-001');
    assert.strictEqual(job2.id, 'grading-sub-dup-001');
  });
});
