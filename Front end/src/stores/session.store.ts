import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

export type UserRole = 'ADMIN' | 'TEACHER' | 'STUDENT';

export interface UserSession {
  id: string; // Opaque ID
  email: string;
  role: UserRole;
  name?: string;
  fullName?: string;
}

interface SessionState {
  user: UserSession | null;
  isAuthenticated: boolean;
  setSession: (user: UserSession) => void;
  clearSession: () => void;
}

/**
 * Client-side UI session cache.
 * Stored in sessionStorage so each tab maintains its local UI context.
 * NOTE: The backend HttpOnly session cookie is the SOLE authority for authentication.
 * No access or refresh tokens are stored here.
 */
export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      user: null,
      isAuthenticated: false,
      setSession: (user) => set({ user, isAuthenticated: true }),
      clearSession: () => set({ user: null, isAuthenticated: false }),
    }),
    {
      name: 'codeassess-session',
      storage: createJSONStorage(() => sessionStorage),
    }
  )
);
