import axios from 'axios';

export interface ParsedApiError {
  status?: number;
  code?: string;
  message?: string;
  isNetworkError: boolean;
  isServerError: boolean;
  isConflict: boolean;
  isUnauthorized: boolean;
  isForbidden: boolean;
  isBadRequest: boolean;
}

/**
 * Parses any API error safely without relying solely on `instanceof AxiosError`.
 * Works across bundled, SSR, cross-realm, or custom wrapped errors.
 */
export function parseApiError(error: unknown): ParsedApiError {
  let status: number | undefined;
  let code: string | undefined;
  let message: string | undefined;
  let isNetworkError = false;

  if (typeof error === 'object' && error !== null) {
    const candidate = error as Record<string, any>;

    // 1. Check for response status (Axios or Fetch response)
    if (candidate.response && typeof candidate.response === 'object') {
      const resp = candidate.response;
      if (typeof resp.status === 'number') {
        status = resp.status;
      }
      if (resp.data && typeof resp.data === 'object') {
        message = resp.data.message || resp.data.error || resp.data.detail;
        code = resp.data.error || resp.data.code;
      } else if (typeof resp.data === 'string') {
        message = resp.data;
      }
    } 
    // 2. Check for network / connection failure (request made but no response)
    else if (
      candidate.request ||
      candidate.code === 'ERR_NETWORK' ||
      candidate.code === 'ECONNREFUSED' ||
      candidate.code === 'ETIMEDOUT'
    ) {
      isNetworkError = true;
    }

    // 3. Check for direct status property (e.g. wrapped error object)
    if (status === undefined && typeof candidate.status === 'number') {
      status = candidate.status;
    }

    // 4. Check for direct code or message property
    if (!code && typeof candidate.code === 'string') {
      code = candidate.code;
    }
    if (!message && typeof candidate.message === 'string') {
      message = candidate.message;
      if (
        message.toLowerCase().includes('network error') ||
        message.toLowerCase().includes('failed to fetch') ||
        message.toLowerCase().includes('connection refused')
      ) {
        isNetworkError = true;
      }
    }
  } else if (typeof error === 'string') {
    message = error;
    if (
      message.toLowerCase().includes('network error') ||
      message.toLowerCase().includes('failed to fetch')
    ) {
      isNetworkError = true;
    }
  }

  const isConflict = status === 409;
  const isUnauthorized = status === 401;
  const isForbidden = status === 403;
  const isBadRequest = status === 400;
  const isServerError = typeof status === 'number' && status >= 500 && status < 600;

  return {
    status,
    code,
    message,
    isNetworkError,
    isServerError,
    isConflict,
    isUnauthorized,
    isForbidden,
    isBadRequest,
  };
}
