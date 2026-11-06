/**
 * Utility functions for question titles and date formatting.
 */

/**
 * Strips duplicate numbering prefixes like "Câu 1: ", "Question 1: ", "Bài 1 - "
 * while preserving the actual problem title.
 */
export function formatQuestionTitle(title?: string): string {
  if (!title) return '';
  return title.replace(/^(câu|question|bài)\s*\d+\s*[:.-]\s*/i, '').trim();
}

/**
 * Safely parses any date representation (ISO string, string with space, Jackson array, timestamp)
 * into a local "YYYY-MM-DDTHH:mm" string for <input type="datetime-local">.
 */
export function parseDateToLocalDateTimeInput(val: unknown): string {
  if (!val) return '';
  try {
    let d: Date;
    if (Array.isArray(val) && val.length >= 3) {
      const [year, month, day, hour = 0, minute = 0] = val;
      d = new Date(year, month - 1, day, hour, minute);
      const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
      return local.toISOString().slice(0, 16);
    } else if (typeof val === 'string') {
      const normalized = val.replace(' ', 'T');
      d = new Date(normalized);
    } else if (typeof val === 'number') {
      d = new Date(val);
    } else if (val instanceof Date) {
      d = val;
    } else {
      return '';
    }

    if (isNaN(d.getTime())) return '';
    const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
  } catch {
    return '';
  }
}

