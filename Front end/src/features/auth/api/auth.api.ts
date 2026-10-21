import { apiClient } from '@/lib/api-client';
import { LoginFormData, ForgotPasswordFormData, ResetPasswordFormData, RegisterFormData } from '../schemas/auth.schema';
import { UserSession } from '@/stores/session.store';

interface LoginResponse {
  user: UserSession;
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

  getMe: async (): Promise<UserSession> => {
    const response = await apiClient.get<UserSession>('/auth/me');
    return response.data;
  },

  forgotPassword: async (data: ForgotPasswordFormData): Promise<{ message: string }> => {
    const response = await apiClient.post<{ message: string }>('/auth/forgot-password', data);
    return response.data;
  },

  resendOtp: async (data: { email: string }): Promise<{ message: string; cooldownSeconds?: number }> => {
    const response = await apiClient.post<{ message: string; cooldownSeconds?: number }>('/auth/resend-otp', data);
    return response.data;
  },

  verifyOtp: async (data: { email: string; otp: string }): Promise<{ resetToken: string; message: string }> => {
    const response = await apiClient.post<{ resetToken: string; message: string }>('/auth/verify-otp', data);
    return response.data;
  },

  resetPassword: async (token: string, data: ResetPasswordFormData): Promise<{ message: string }> => {
    const response = await apiClient.post<{ message: string }>('/auth/reset-password', { token, ...data });
    return response.data;
  },
};
