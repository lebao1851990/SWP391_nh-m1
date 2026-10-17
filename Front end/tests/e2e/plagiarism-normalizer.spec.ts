import { test, expect } from '@playwright/test';
import {
  parseStrictBoolean,
  parseSimilarityRate,
  normalizeSimilarityPair,
} from '../../src/features/plagiarism/api';
import { login } from './helpers';

test.describe('Plagiarism API Normalizer Unit Tests', () => {

  test('1. Preserves legitimate similarity values, including 0 and decimals', () => {
    expect(parseSimilarityRate(0)).toBe(0);
    expect(parseSimilarityRate(100)).toBe(100);
    expect(parseSimilarityRate(74.55)).toBe(74.55);
    expect(parseSimilarityRate('0')).toBe(0);
    expect(parseSimilarityRate('82.3')).toBe(82.3);

    // Invalid non-finite values return undefined
    expect(parseSimilarityRate(NaN)).toBeUndefined();
    expect(parseSimilarityRate(Infinity)).toBeUndefined();
    expect(parseSimilarityRate('not-a-number')).toBeUndefined();
    expect(parseSimilarityRate(null)).toBeUndefined();
    expect(parseSimilarityRate(undefined)).toBeUndefined();
  });

  test('2. Strictly parses boolean forms without string-to-truthy coercion', () => {
    expect(parseStrictBoolean(true)).toBe(true);
    expect(parseStrictBoolean(false)).toBe(false);
    expect(parseStrictBoolean('true')).toBe(true);
    expect(parseStrictBoolean('TRUE')).toBe(true);
    expect(parseStrictBoolean('false')).toBe(false);
    expect(parseStrictBoolean('FALSE')).toBe(false);

    // Crucial: "false" MUST NOT become true
    expect(parseStrictBoolean('false')).not.toBe(true);

    // Arbitrary strings, objects, numbers, and null/undefined return undefined
    expect(parseStrictBoolean('invalid')).toBeUndefined();
    expect(parseStrictBoolean(0)).toBeUndefined();
    expect(parseStrictBoolean(1)).toBeUndefined();
    expect(parseStrictBoolean(null)).toBeUndefined();
    expect(parseStrictBoolean(undefined)).toBeUndefined();
  });

  test('3. Correctly normalizes valid flat SimilarityPairDto from active backend', () => {
    const rawBackendDto = {
      pairId: '10357',
      submissionAId: 'sub-aaa-111',
      submissionBId: 'sub-bbb-222',
      studentAId: 'usr-student-a',
      studentBId: 'usr-student-b',
      studentAName: 'Student B',
      studentBName: 'Student A',
      similarityRate: 100,
      flagged: true,
    };

    const record = normalizeSimilarityPair(rawBackendDto);

    expect(record.recordId).toBe('10357');
    expect(record.pair.pairId).toBe('10357');
    expect(record.pair.submissionAId).toBe('sub-aaa-111');
    expect(record.pair.submissionBId).toBe('sub-bbb-222');
    expect(record.pair.studentAId).toBe('usr-student-a');
    expect(record.pair.studentBId).toBe('usr-student-b');
    expect(record.pair.studentAName).toBe('Student B');
    expect(record.pair.studentBName).toBe('Student A');
    expect(record.pair.similarityRate).toBe(100);
    expect(record.pair.isFlagged).toBe(true);
  });

  test('4. Correctly normalizes valid flat record with similarityRate = 0 and flagged = false', () => {
    const rawZeroDto = {
      pairId: '10358',
      submissionAId: 'sub-aaa-111',
      submissionBId: 'sub-ccc-333',
      studentAId: 'usr-student-a',
      studentBId: 'usr-student-c',
      similarityRate: 0,
      flagged: false,
    };

    const record = normalizeSimilarityPair(rawZeroDto);

    expect(record.pair.similarityRate).toBe(0);
    expect(record.pair.isFlagged).toBe(false);
  });

  test('5. Correctly normalizes legacy or nested pair structures', () => {
    const legacyNested = {
      recordId: 'rec-legacy-99',
      examName: 'Midterm Exam',
      className: 'Class SE101',
      pair: {
        pairId: 'rec-legacy-99',
        submissionAId: 'sub-1',
        submissionBId: 'sub-2',
        studentAId: 'stu-1',
        studentBId: 'stu-2',
        similarityRate: 85.5,
        isFlagged: true,
      },
      evidence: [],
    };

    const record = normalizeSimilarityPair(legacyNested);

    expect(record.recordId).toBe('rec-legacy-99');
    expect(record.examName).toBe('Midterm Exam');
    expect(record.className).toBe('Class SE101');
    expect(record.pair.similarityRate).toBe(85.5);
    expect(record.pair.isFlagged).toBe(true);
  });

  test('6. Throws explicit error when backend authoritative flag is absent (does NOT infer from >=70)', () => {
    const missingFlagDto = {
      pairId: '10359',
      submissionAId: 'sub-1',
      submissionBId: 'sub-2',
      similarityRate: 95.0, // High rate, but flag is absent!
      // flagged is missing
      // isFlagged is missing
    };

    expect(() => normalizeSimilarityPair(missingFlagDto)).toThrow(
      /missing or invalid authoritative boolean flag/i
    );
  });

  test('7. Throws explicit error when string "false" is passed as an invalid non-boolean value in unstrict field or missing identifiers', () => {
    // Missing submission IDs
    const missingSubIds = {
      pairId: '10360',
      similarityRate: 50,
      flagged: false,
    };
    expect(() => normalizeSimilarityPair(missingSubIds)).toThrow(
      /missing submission identifiers/i
    );

    // Missing pairId
    const missingPairId = {
      submissionAId: 'sub-1',
      submissionBId: 'sub-2',
      similarityRate: 50,
      flagged: false,
    };
    expect(() => normalizeSimilarityPair(missingPairId)).toThrow(
      /missing required pairId/i
    );

    // Invalid non-numeric similarityRate
    const invalidRate = {
      pairId: '10361',
      submissionAId: 'sub-1',
      submissionBId: 'sub-2',
      similarityRate: 'corrupted-data',
      flagged: true,
    };
    expect(() => normalizeSimilarityPair(invalidRate)).toThrow(
      /missing or invalid similarityRate/i
    );

    // Null or non-object item
    expect(() => normalizeSimilarityPair(null)).toThrow(
      /expected a non-null object/i
    );
    expect(() => normalizeSimilarityPair('string-primitive')).toThrow(
      /expected a non-null object/i
    );
  });

  test('8. UI Consumer: Displays explicit error notice (no empty disguise, no crash) when API returns malformed response', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');

    // Intercept subsequent request with malformed non-array object
    await page.route('**/api/teacher/plagiarism', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ notAnArray: true, corrupted: 'malformed_payload' }),
      });
    });

    await page.goto('/teacher/plagiarism');

    // Asserts explicit error state UI rendered
    await expect(page.locator('h2:has-text("Integration Notice")')).toBeVisible({ timeout: 8000 });
    await expect(page.locator('text=Malformed API response')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('button:has-text("Retry Connection")')).toBeVisible({ timeout: 5000 });

    // Asserts it did NOT silently show empty table
    await expect(page.locator('text=No plagiarism records detected')).not.toBeVisible();
  });

  test('9. UI Consumer: Distinguishes legitimate empty array from error state', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');

    // Intercept subsequent request with valid empty array
    await page.route('**/api/teacher/plagiarism', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto('/teacher/plagiarism');

    // Asserts legitimate empty state rendered
    await expect(page.locator('text=No plagiarism records detected for your active exams')).toBeVisible({ timeout: 8000 });

    // Asserts error notice is NOT rendered
    await expect(page.locator('h2:has-text("Integration Notice")')).not.toBeVisible();
  });
});
