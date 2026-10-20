import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { parseApiError } from '../../src/lib/api-error.ts';

describe('Auth Error Parser Unit Tests', () => {

  test('1. Detects HTTP 409 Conflict without instanceof AxiosError', () => {
    const duckTyped409 = {
      name: 'CustomError',
      message: 'Request failed with status code 409',
      response: {
        status: 409,
        data: {
          statusCode: 409,
          error: 'CONFLICT',
          message: 'Email already exists: kindragon987@gmail.com',
        },
      },
    };

    const parsed = parseApiError(duckTyped409);
    assert.equal(parsed.isConflict, true);
    assert.equal(parsed.status, 409);
    assert.equal(parsed.isNetworkError, false);
    assert.equal(parsed.isUnauthorized, false);
    assert.equal(parsed.message, 'Email already exists: kindragon987@gmail.com');
  });

  test('2. Detects network connection errors without status code', () => {
    const networkErr = {
      name: 'AxiosError',
      code: 'ERR_NETWORK',
      message: 'Network Error',
      request: {},
    };

    const parsed = parseApiError(networkErr);
    assert.equal(parsed.isNetworkError, true);
    assert.equal(parsed.isConflict, false);
    assert.equal(parsed.isUnauthorized, false);
    assert.equal(parsed.status, undefined);
  });

  test('3. Detects 401 Unauthorized invalid credentials', () => {
    const authErr = {
      response: {
        status: 401,
        data: {
          statusCode: 401,
          error: 'UNAUTHORIZED',
          message: 'Authentication failed.',
        },
      },
    };

    const parsed = parseApiError(authErr);
    assert.equal(parsed.isUnauthorized, true);
    assert.equal(parsed.isNetworkError, false);
    assert.equal(parsed.isConflict, false);
    assert.equal(parsed.status, 401);
  });

  test('4. Detects 403 Forbidden / Locked account', () => {
    const forbiddenErr = {
      response: {
        status: 403,
        data: {
          statusCode: 403,
          error: 'FORBIDDEN',
          message: 'Account is locked.',
        },
      },
    };

    const parsed = parseApiError(forbiddenErr);
    assert.equal(parsed.isForbidden, true);
    assert.equal(parsed.status, 403);
  });

  test('5. Detects 400 Bad Request / Validation error', () => {
    const badReqErr = {
      response: {
        status: 400,
        data: {
          statusCode: 400,
          error: 'BAD_REQUEST',
          message: 'Validation error',
        },
      },
    };

    const parsed = parseApiError(badReqErr);
    assert.equal(parsed.isBadRequest, true);
    assert.equal(parsed.status, 400);
  });

  test('6. Detects 500 Server Error', () => {
    const serverErr = {
      response: {
        status: 500,
        data: {
          statusCode: 500,
          error: 'INTERNAL_SERVER_ERROR',
          message: 'An unexpected error occurred.',
        },
      },
    };

    const parsed = parseApiError(serverErr);
    assert.equal(parsed.isServerError, true);
    assert.equal(parsed.status, 500);
  });
});
