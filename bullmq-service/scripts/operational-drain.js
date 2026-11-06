const { Queue } = require('bullmq');
const config = require('../src/config');

/**
 * Operational Quiescence & Drain Script for BullMQ Grading Queue.
 * Follows the 6-state quiescence gate approved in TASK-IMPL-01D:
 * 1. Pause queue (blocks new pickups)
 * 2. Wait for in-flight active workers to terminate (bounded by lease window)
 * 3. Audit all 6 states: active, waiting, delayed, prioritized, paused, failed
 * 4. Preserves failed-job evidence (no auto-purge)
 * 5. Exits 0 only when fully quiesced; exits 1 to abort migration if work remains.
 */
async function drainAndQuiesce() {
  console.log('=== BULLMQ OPERATIONAL QUIESCENCE GATE ===');
  const queue = new Queue(config.queue.name, { connection: config.redis });

  try {
    console.log('[Step 1] Pausing queue to prevent workers from pulling new jobs...');
    await queue.pause();
    console.log('         Queue successfully paused.');

    console.log('[Step 2] Checking active jobs...');
    let activeCount = await queue.getActiveCount();
    const startTime = Date.now();
    const timeoutMs = 65000; // 65s (gracefully exceeds 60s Docker sandbox lease)

    while (activeCount > 0 && Date.now() - startTime < timeoutMs) {
      console.log(`         Waiting for ${activeCount} active grading sandbox tasks to finish...`);
      await new Promise(resolve => setTimeout(resolve, 3000));
      activeCount = await queue.getActiveCount();
    }

    if (activeCount > 0) {
      console.error(`[FATAL] ${activeCount} active jobs still running after ${timeoutMs / 1000}s!`);
      console.error('        Abort migration. Stop worker containers manually.');
      process.exit(1);
    }
    console.log('         All active tasks completed or leases expired.');

    console.log('[Step 3] Auditing all BullMQ states...');
    const counts = await queue.getJobCounts('active', 'waiting', 'delayed', 'prioritized', 'paused', 'failed');
    console.log('         State counts:', JSON.stringify(counts, null, 2));

    const inFlightCount = counts.active + counts.waiting + counts.delayed + counts.prioritized;
    if (inFlightCount !== 0) {
      console.error(`[FATAL] Queue is NOT empty! In-flight jobs remaining: ${inFlightCount}`);
      console.error('        Please inspect delayed/waiting jobs before proceeding.');
      process.exit(1);
    }

    if (counts.failed > 0) {
      console.warn(`[WARN] Found ${counts.failed} dead-letter (failed) jobs in queue.`);
      console.warn('       Preserving failed job records as required by data integrity policy.');
    }

    console.log('✅ BullMQ is 100% quiesced and safe for backend/worker shutdown.');
    await queue.close();
    process.exit(0);
  } catch (err) {
    console.error('[ERROR] Quiescence inspection failed:', err.message);
    try { await queue.close(); } catch (_) {}
    process.exit(1);
  }
}

drainAndQuiesce();

