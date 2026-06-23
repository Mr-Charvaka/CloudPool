import { CloudPoolClient } from './index';

export interface KvEntry {
  key: string;
  value: any;
  createdAt?: string;
  updatedAt?: string;
}

export class KvClient {
  constructor(private readonly client: CloudPoolClient) {}

  public async get<T = unknown>(key: string): Promise<T> {
    const response = await this.client.request(
      'GET',
      `/api/v1/kv/${encodeURIComponent(key)}`
    );
    const json = await response.json();
    return json.value;
  }

  public async set<T = unknown>(
    key: string,
    value: any,
    ttlSeconds?: number
  ): Promise<void> {
    const body: Record<string, any> = { value };
    if (ttlSeconds !== undefined) body.ttlSeconds = ttlSeconds;
    await this.client.request(
      'PUT',
      `/api/v1/kv/${encodeURIComponent(key)}`,
      {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      }
    );
  }

  public async delete<T = unknown>(key: string): Promise<void> {
    await this.client.request(
      'DELETE',
      `/api/v1/kv/${encodeURIComponent(key)}`
    );
  }

  public async list<T = unknown>(prefix?: string): Promise<KvEntry[]> {
    const query = prefix
      ? `?prefix=${encodeURIComponent(prefix)}`
      : '';
    const response = await this.client.request(
      'GET',
      `/api/v1/kv${query}`
    );
    return response.json();
  }
}
