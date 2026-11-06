const assert = require('node:assert');
const { DockerRunner, normalizeOutput } = require('../src/docker-runner');

console.log('Running Sandbox Service Gatekeeper Unit Tests...');

// Test 1: Gatekeeper rejects unauthorized image
assert.throws(() => {
  DockerRunner.validateGatekeeperPolicy({
    Image: 'ubuntu:latest',
    NetworkDisabled: true,
  });
}, /Image ubuntu:latest is unauthorized/);
console.log('✔ Test 1: Gatekeeper rejects unauthorized image passed');

// Test 2: Gatekeeper rejects privileged mode
assert.throws(() => {
  DockerRunner.validateGatekeeperPolicy({
    Image: 'eclipse-temurin:21-jdk-alpine',
    NetworkDisabled: true,
    HostConfig: { Privileged: true },
  });
}, /Privileged containers are strictly forbidden/);
console.log('✔ Test 2: Gatekeeper rejects privileged mode passed');

// Test 3: Gatekeeper rejects volume binds
assert.throws(() => {
  DockerRunner.validateGatekeeperPolicy({
    Image: 'eclipse-temurin:21-jdk-alpine',
    NetworkDisabled: true,
    HostConfig: { Binds: ['/:/host'] },
  });
}, /Host volume bind mounts are strictly forbidden/);
console.log('✔ Test 3: Gatekeeper rejects volume binds passed');

// Test 4: Gatekeeper rejects enabled networking
assert.throws(() => {
  DockerRunner.validateGatekeeperPolicy({
    Image: 'eclipse-temurin:21-jdk-alpine',
    NetworkDisabled: false,
    HostConfig: { ReadonlyRootfs: true },
  });
}, /Network must be disabled/);
console.log('✔ Test 4: Gatekeeper rejects enabled networking passed');

// Test 5: Gatekeeper accepts compliant hardened container
assert.doesNotThrow(() => {
  DockerRunner.validateGatekeeperPolicy({
    Image: 'eclipse-temurin:21-jdk-alpine',
    NetworkDisabled: true,
    HostConfig: {
      Privileged: false,
      Binds: [],
      Memory: 536870912,
      NanoCPUs: 500000000,
      PidsLimit: 50,
      ReadonlyRootfs: true,
    },
  });
});
console.log('✔ Test 5: Gatekeeper accepts compliant hardened container passed');

// Test 6: Output Normalizer
assert.strictEqual(normalizeOutput('hello \r\nworld  \n'), 'hello\nworld');
console.log('✔ Test 6: Output normalizer passed');

console.log('ALL SANDBOX SERVICE UNIT TESTS PASSED!');

