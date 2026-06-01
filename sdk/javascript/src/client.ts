import { StorageClient } from './storage';
import { DatabaseClient } from './database';
import { VectorClient } from './vector';
import { ProjectClient } from './projects';
import { AuthClient } from './auth';
import type { CloudPoolConfig } from './types';

/**
 * Main CloudPool SDK client.
 * Initialize once and reuse across your application.
 */
export class CloudPool {
  public readonly storage: StorageClient;
  public readonly database: DatabaseClient;
  public readonly vector: VectorClient;
  public readonly projects: ProjectClient;
  public readonly auth: AuthClient;

  private readonly baseUrl: string;
  private readonly apiKey?: string;
  private authToken?: string;

  constructor(config: CloudPoolConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, '');
    this.apiKey = config.apiKey;
    this.authToken = config.token;

    this.storage = new StorageClient(this);
    this.database = new DatabaseClient(this);
    this.vector = new VectorClient(this);
    this.projects = new ProjectClient(this);
    this.auth = new AuthClient(this);
  }

  /**
   * Make an authenticated HTTP request to CloudPool's REST API.
   * Used internally by all sub-clients.
   */
  async request<T>(
    method: 'GET' | 'POST' | 'PUT' | 'DELETE',
    path: string,
    body?: unknown,
  ): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };

    if (this.apiKey) {
      headers['X-API-Key'] = this.apiKey;
    } else if (this.authToken) {
      headers['Authorization'] = `Bearer ${this.authToken}`;
    }

    const response = await fetch(`${this.baseUrl}${path}`, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });

    if (!response.ok) {
      const error = await response.text();
      throw new Error(`CloudPool API error (${response.status}): ${error}`);
    }

    return response.json() as Promise<T>;
  }

  /** Set a JWT token after login */
  setToken(token: string) {
    this.authToken = token;
  }

  getBaseUrl() {
    return this.baseUrl;
  }
}
