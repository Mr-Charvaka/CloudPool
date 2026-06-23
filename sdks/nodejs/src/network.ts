import { CloudPoolClient } from './index';

export interface CreateTunnelConfig {
  name: string;
  targetPort: number;
  protocol?: string;
}

export interface CreatePubSubUserConfig {
  username: string;
  password?: string;
  permissions?: string[];
}

export interface CreateWafRuleConfig {
  name: string;
  action: string;
  pattern: string;
  priority?: number;
}

export class NetworkClient {
  constructor(private readonly client: CloudPoolClient) {}

  public async listTunnels<T = unknown>(): Promise<T[]> {
    const response = await this.client.request(
      'GET',
      '/api/v1/network/tunnels'
    );
    return response.json();
  }

  public async createTunnel<T = unknown>(config: CreateTunnelConfig): Promise<T> {
    const response = await this.client.request(
      'POST',
      '/api/v1/network/tunnels',
      {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
      }
    );
    return response.json();
  }

  public async deleteTunnel<T = unknown>(id: string): Promise<void> {
    await this.client.request(
      'DELETE',
      `/api/v1/network/tunnels/${encodeURIComponent(id)}`
    );
  }

  public async listDomains<T = unknown>(): Promise<T[]> {
    const response = await this.client.request(
      'GET',
      '/api/v1/network/domains'
    );
    return response.json();
  }

  public async addDomain<T = unknown>(domain: string): Promise<T> {
    const response = await this.client.request(
      'POST',
      '/api/v1/network/domains',
      {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ domain }),
      }
    );
    return response.json();
  }

  public async removeDomain<T = unknown>(id: string): Promise<void> {
    await this.client.request(
      'DELETE',
      `/api/v1/network/domains/${encodeURIComponent(id)}`
    );
  }

  public async listPubSubUsers<T = unknown>(): Promise<T[]> {
    const response = await this.client.request(
      'GET',
      '/api/v1/network/pubsub/users'
    );
    return response.json();
  }

  public async createPubSubUser<T = unknown>(
    config: CreatePubSubUserConfig
  ): Promise<T> {
    const response = await this.client.request(
      'POST',
      '/api/v1/network/pubsub/users',
      {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
      }
    );
    return response.json();
  }

  public async deletePubSubUser<T = unknown>(id: string): Promise<void> {
    await this.client.request(
      'DELETE',
      `/api/v1/network/pubsub/users/${encodeURIComponent(id)}`
    );
  }

  public async listWafRules<T = unknown>(): Promise<T[]> {
    const response = await this.client.request(
      'GET',
      '/api/v1/network/waf'
    );
    return response.json();
  }

  public async createWafRule<T = unknown>(config: CreateWafRuleConfig): Promise<T> {
    const response = await this.client.request(
      'POST',
      '/api/v1/network/waf',
      {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
      }
    );
    return response.json();
  }

  public async deleteWafRule<T = unknown>(id: string): Promise<void> {
    await this.client.request(
      'DELETE',
      `/api/v1/network/waf/${encodeURIComponent(id)}`
    );
  }
}
