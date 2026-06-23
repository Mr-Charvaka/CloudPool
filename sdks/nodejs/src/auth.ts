import { CloudPoolClient } from './index';

export interface LoginResponse {
  token: string;
  refreshToken?: string;
  user: any;
}

export interface RegisterParams {
  email: string;
  password: string;
  name?: string;
}

export interface UserProfile {
  id: string;
  email: string;
  name?: string;
  [key: string]: any;
}

export class AuthClient {
  constructor(private readonly client: CloudPoolClient) {}

  public async me<T = unknown>(): Promise<UserProfile> {
    const response = await this.client.request('GET', '/api/v1/auth/me');
    return response.json();
  }

  public async login<T = unknown>(email: string, password: string): Promise<LoginResponse> {
    const response = await this.client.request('POST', '/api/v1/auth/login', {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });
    return response.json();
  }

  public async register<T = unknown>(params: RegisterParams): Promise<LoginResponse> {
    const response = await this.client.request(
      'POST',
      '/api/v1/auth/register',
      {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params),
      }
    );
    return response.json();
  }

  public async refreshToken<T = unknown>(refreshToken: string): Promise<LoginResponse> {
    const response = await this.client.request(
      'POST',
      '/api/v1/auth/refresh',
      {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      }
    );
    return response.json();
  }
}
