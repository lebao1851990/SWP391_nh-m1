const express = require('express');
const { DockerRunner } = require('./docker-runner');

const app = express();
const PORT = process.env.PORT || 8085;
const INTERNAL_API_SECRET = process.env.INTERNAL_API_SECRET;

app.use(express.json({ limit: '10mb' }));

// Healthcheck endpoint (Public to internal network)
app.get('/api/health', (req, res) => {
  res.json({
    status: 'UP',
    service: 'codeassess-sandbox-service',
    timestamp: new Date().toISOString(),
  });
});

// Authentication middleware
function authenticateInternalRequest(req, res, next) {
  const token = req.header('X-Internal-Token');
  if (!token || (INTERNAL_API_SECRET && token !== INTERNAL_API_SECRET)) {
    return res.status(401).json({
      error: 'Unauthorized: Invalid or missing X-Internal-Token',
    });
  }
  next();
}

// Prohibited infrastructure parameter checker
function forbidInfrastructureParameters(req, res, next) {
  const forbiddenKeys = [
    'image',
    'dockerHost',
    'privileged',
    'binds',
    'volumes',
    'network',
    'capabilities',
    'securityOpt',
    'hostConfig',
    'entrypoint',
    'cmd',
  ];

  for (const key of forbiddenKeys) {
    if (req.body && req.body[key] !== undefined) {
      return res.status(400).json({
        error: `Bad Request: Prohibited parameter '${key}' rejected by Sandbox Gatekeeper.`,
      });
    }
  }
  next();
}

// Main test suite execution endpoint
app.post(
  '/api/v1/sandbox/run-suite',
  authenticateInternalRequest,
  forbidInfrastructureParameters,
  async (req, res) => {
    try {
      const { sourceCode, testCases, mainClassName, timeoutSeconds, submissionId } = req.body;

      if (!sourceCode || typeof sourceCode !== 'string') {
        return res.status(400).json({ error: 'sourceCode is required and must be a string' });
      }

      if (Buffer.byteLength(sourceCode, 'utf-8') > 65536) {
        return res.status(400).json({ error: 'sourceCode exceeds max allowed length of 65536 bytes' });
      }

      if (!Array.isArray(testCases) || testCases.length === 0) {
        return res.status(400).json({ error: 'testCases must be a non-empty array' });
      }

      console.log(`[Sandbox] Executing suite for submission: ${submissionId || 'direct'} with ${testCases.length} test cases`);

      const result = await DockerRunner.runTestCaseSuite({
        sourceCode,
        testCases,
        mainClassName,
        timeoutSeconds: Number(timeoutSeconds) || 10,
      });

      return res.json(result);
    } catch (err) {
      console.error('[Sandbox] Execution error:', err.message);
      return res.status(500).json({
        error: 'Sandbox execution failed: ' + err.message,
      });
    }
  }
);

// Fallback 404
app.use((req, res) => {
  res.status(404).json({ error: 'Endpoint not found' });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`[Sandbox] Dedicated Sandbox Service listening on 0.0.0.0:${PORT}`);
});

