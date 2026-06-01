import type { CloudPool } from './client';
import type { AuthResponse, LoginRequest, User } from './types';

export class AuthClient {
  constructor(private readonly cp: CloudPool) {}

  /** Register a new account */
  async register(fullName: string, email: string, password: string): Promise<AuthResponse> {
    const res = await this.cp.request<AuthResponse>('POST', '/api/auth/register', {
      fullName,
      email,
      password,
    });
    this.cp.setToken(res.token);
    return res;
  }

  /** Sign in and store the token automatically */
  async login(email: string, password: string): Promise<AuthResponse> {
    const res = await this.cp.request<AuthResponse>('POST', '/api/auth/login', { email, password });
    this.cp.setToken(res.token);
    return res;
  }

  /** Get the currently authenticated user */
  async me(): Promise<User> {
    return this.cp.request<User>('GET', '/api/auth/me');
  }
}
