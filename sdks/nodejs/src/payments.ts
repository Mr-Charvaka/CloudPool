import { CloudPoolClient } from './index';

export interface ConnectGatewayConfig {
  provider: string;
  apiKey: string;
  webhookSecret?: string;
}

export interface CreateCheckoutConfig {
  planId: string;
  successUrl: string;
  cancelUrl: string;
  metadata?: Record<string, string>;
}

export class PaymentsClient {
  constructor(private readonly client: CloudPoolClient) {}

  public async listGateways<T = unknown>(): Promise<T[]> {
    const response = await this.client.request(
      'GET',
      '/api/v1/payments/gateways'
    );
    return response.json();
  }

  public async connectGateway<T = unknown>(config: ConnectGatewayConfig): Promise<T> {
    const response = await this.client.request(
      'POST',
      '/api/v1/payments/gateways',
      {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
      }
    );
    return response.json();
  }

  public async disconnectGateway<T = unknown>(id: string): Promise<void> {
    await this.client.request(
      'DELETE',
      `/api/v1/payments/gateways/${encodeURIComponent(id)}`
    );
  }

  public async listPlans<T = unknown>(): Promise<T[]> {
    const response = await this.client.request(
      'GET',
      '/api/v1/payments/plans'
    );
    return response.json();
  }

  public async listInvoices<T = unknown>(params?: {
    limit?: number;
    status?: string;
  }): Promise<T[]> {
    const searchParams = new URLSearchParams();
    if (params?.limit) searchParams.set('limit', String(params.limit));
    if (params?.status) searchParams.set('status', params.status);
    const query = searchParams.toString();
    const response = await this.client.request(
      'GET',
      `/api/v1/payments/invoices${query ? '?' + query : ''}`
    );
    return response.json();
  }

  public async createCheckout<T = unknown>(config: CreateCheckoutConfig): Promise<T> {
    const response = await this.client.request(
      'POST',
      '/api/v1/payments/checkout',
      {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
      }
    );
    return response.json();
  }

  public async listUsage<T = unknown>(params?: {
    startDate?: string;
    endDate?: string;
  }): Promise<T[]> {
    const searchParams = new URLSearchParams();
    if (params?.startDate)
      searchParams.set('startDate', params.startDate);
    if (params?.endDate) searchParams.set('endDate', params.endDate);
    const query = searchParams.toString();
    const response = await this.client.request(
      'GET',
      `/api/v1/payments/usage${query ? '?' + query : ''}`
    );
    return response.json();
  }
}
