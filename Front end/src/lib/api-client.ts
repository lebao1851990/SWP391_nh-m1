import axios from 'axios';
import { useSessionStore } from '@/stores/session.store';

// We assume the backend is hosted at NEXT_PUBLIC_API_URL or defaults to localhost
export const apiClient = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Response Interceptor: Handle 401 & 403 globally
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      if (error.response.status === 401) {
        // Token expired or invalid
        useSessionStore.getState().clearSession();
        if (typeof window !== 'undefined') {
          const pathname = window.location.pathname;
          const search = window.location.search;
          if (pathname !== '/login' && pathname !== '/dev/preview' && !pathname.startsWith('/dev/preview/')) {
            // Ensure redirect target is a safe internal path (starts with / but not //)
            const fullPath = pathname + search;
            const isSafePath = fullPath.startsWith('/') && !fullPath.startsWith('//');
            
            let redirectUrl = '/login';
            if (isSafePath) {
              redirectUrl += `?redirect=${encodeURIComponent(fullPath)}`;
            }
            window.location.href = redirectUrl;
          }
        }
      } else if (error.response.status === 403) {
        // Forbidden: Valid token but lacks permission for resource
        // We log this, but the UI should handle the specific feedback
        console.warn('Access denied to resource (403 Forbidden).', error.config.url);
      }
    }
    return Promise.reject(error);
  }
);
