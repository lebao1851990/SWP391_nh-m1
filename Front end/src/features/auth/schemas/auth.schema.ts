import { z } from 'zod';

export const loginSchema = z.object({
  identity: z.string().min(1, 'Please enter your university ID or email'),
  password: z.string().min(1, 'Password is required for credentials authentication'),
  rememberMe: z.boolean().optional().default(false),
});

export type LoginFormData = z.infer<typeof loginSchema>;

export const forgotPasswordSchema = z.object({
  identity: z.string().min(1, 'Please enter your university ID or email'),
});

export type ForgotPasswordFormData = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z.object({
  password: z.string().min(8, 'Min. 8 characters required'),
  confirmPassword: z.string().min(1, 'Please confirm your password'),
}).refine((data) => data.password === data.confirmPassword, {
  message: 'Passwords do not match',
  path: ['confirmPassword'],
});

export type ResetPasswordFormData = z.infer<typeof resetPasswordSchema>;

export const registerSchema = z.object({
  fullName: z.string().min(1, 'Full name is required.'),
  email: z.string().min(1, 'Email is required.').email('Please enter a valid university email address.'),
  accountRole: z.enum(['student', 'teacher']),
  password: z.string()
    .min(10, 'Password must be at least 10 characters')
    .regex(/[A-Z]/, 'Password must contain at least one uppercase letter')
    .regex(/[a-z]/, 'Password must contain at least one lowercase letter')
    .regex(/[0-9]/, 'Password must contain at least one number')
    .regex(/[!@#$%^&*(),.?":{}|<>]/, 'Password must contain a special symbol (!@#$)'),
  confirmPassword: z.string().min(1, 'Please confirm your password'),
  terms: z.literal(true, {
    error: () => ({ message: 'You must agree to the Code of Academic Integrity.' }),
  }),
}).refine((data) => data.password === data.confirmPassword, {
  message: 'Passwords do not match.',
  path: ['confirmPassword'],
});

export type RegisterFormData = z.infer<typeof registerSchema>;
