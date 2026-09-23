import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type UserRole = 'ADMIN' | 'TEACHER' | 'STUDENT';

export interface UserSession {
  id: string; // Opaque ID
  email: string;
  role: UserRole;
  name?: string;
}

interface SessionState {
  user: UserSession | null;
  isAuthenticated: boolean;
  setSession: (user: UserSession) => void;
  clearSession: () => void;
}

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
    }
  )
);
