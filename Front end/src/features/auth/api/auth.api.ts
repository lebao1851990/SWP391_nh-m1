import { apiClient } from '@/lib/api-client';
import { LoginFormData, ForgotPasswordFormData, ResetPasswordFormData, RegisterFormData } from '../schemas/auth.schema';
import { UserSession } from '@/stores/session.store';

interface LoginResponse {
  user: UserSession;
  token?: string;
}

export const authApi = {
  register: async (data: RegisterFormData): Promise<void> => {
    await apiClient.post('/auth/register', data);
  },

  login: async (data: LoginFormData): Promise<LoginResponse> => {
    const payload = {
      email: data.identity,
      password: data.password
    };
    const response = await apiClient.post<LoginResponse>('/auth/login', payload);
    return response.data;
  },

  logout: async (): Promise<void> => {
    await apiClient.post('/auth/logout');
  },

  getProfile: async (): Promise<UserSession> => {
    const response = await apiClient.get<UserSession>('/user/profile');
    return response.data;
  },

  forgotPassword: async (data: ForgotPasswordFormData): Promise<void> => {
    await apiClient.post('/auth/forgot-password', data);
  },

  resetPassword: async (token: string, data: ResetPasswordFormData): Promise<void> => {
    await apiClient.post('/auth/reset-password', { token, ...data });
  },
};
