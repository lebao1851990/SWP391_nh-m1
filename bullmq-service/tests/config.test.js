process.env.INTERNAL_API_SECRET = 'test-valid-boot-secret-for-config-test';

const { test, describe } = require('node:test');
const assert = require('node:assert');
const { _validateInternalSecret, _LEGACY_DEFAULT_SECRET } = require('../src/config');

describe('BullMQ Internal API Secret Configuration & Fail-Fast Tests', () => {

  test('Missing secret (null/undefined) fails fast with descriptive error', () => {
    assert.throws(
      () => _validateInternalSecret(undefined),
      /INTERNAL_API_SECRET is missing or empty/
    );
    assert.throws(
      () => _validateInternalSecret(null),
      /INTERNAL_API_SECRET is missing or empty/
    );
  });

  test('Blank or whitespace-only secret fails fast with descriptive error', () => {
    assert.throws(
      () => _validateInternalSecret(''),
      /INTERNAL_API_SECRET is missing or empty/
    );
    assert.throws(
      () => _validateInternalSecret('    '),
      /INTERNAL_API_SECRET is missing or empty/
    );
  });

  test('Known legacy default secret is strictly rejected with descriptive error', () => {
    assert.throws(
      () => _validateInternalSecret('codeassess-internal-secret-2026'),
      /cannot use the insecure legacy default value/
    );
    assert.throws(
      () => _validateInternalSecret('CODEASSESS-INTERNAL-SECRET-2026'),
      /cannot use the insecure legacy default value/
    );
    assert.throws(
      () => _validateInternalSecret('  codeassess-internal-secret-2026  '),
      /cannot use the insecure legacy default value/
    );
  });

  test('Valid non-default secret succeeds and trims whitespace', () => {
    const validSecret = 'super-secure-custom-internal-token-2026';
    const result = _validateInternalSecret(`  ${validSecret}  `);
    assert.strictEqual(result, validSecret);
  });
});
