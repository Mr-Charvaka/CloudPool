import { CloudPoolClient } from './index';

export interface CreateDeploymentConfig {
  name: string;
  image: string;
  replicas?: number;
  env?: Record<string, string>;
  ports?: number[];
  resources?: { cpu?: string; memory?: string };
}

export interface CreateCronJobConfig {
  name: string;
  schedule: string;
  command: string;
  image?: string;
  env?: Record<string, string>;
}

export interface DeployServerlessConfig {
  name: string;
  runtime: string;
  source: string;
  entrypoint?: string;
  env?: Record<string, string>;
  memory?: string;
  timeout?: number;
}

export class ComputeClient {
  constructor(private readonly client: CloudPoolClient) {}

  public async listDeployments<T = unknown>(params?: {
    projectId?: string;
  }): Promise<T[]> {
    const query = params?.projectId
      ? `?projectId=${encodeURIComponent(params.projectId)}`
      : '';
    const response = await this.client.request(
      'GET',
      `/api/v1/compute/deployments${query}`
    );
    return response.json();
  }

  public async createDeployment<T = unknown>(
    config: CreateDeploymentConfig
  ): Promise<T> {
    const response = await this.client.request(
      'POST',
      '/api/v1/compute/deployments',
      {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
      }
    );
    return response.json();
  }

  public async deleteDeployment<T = unknown>(id: string): Promise<void> {
    await this.client.request(
      'DELETE',
      `/api/v1/compute/deployments/${encodeURIComponent(id)}`
    );
  }

  public async listCronJobs<T = unknown>(params?: {
    projectId?: string;
  }): Promise<T[]> {
    const query = params?.projectId
      ? `?projectId=${encodeURIComponent(params.projectId)}`
      : '';
    const response = await this.client.request(
      'GET',
      `/api/v1/compute/cron-jobs${query}`
    );
    return response.json();
  }

  public async createCronJob<T = unknown>(config: CreateCronJobConfig): Promise<T> {
    const response = await this.client.request(
      'POST',
      '/api/v1/compute/cron-jobs',
      {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
      }
    );
    return response.json();
  }

  public async deleteCronJob<T = unknown>(id: string): Promise<void> {
    await this.client.request(
      'DELETE',
      `/api/v1/compute/cron-jobs/${encodeURIComponent(id)}`
    );
  }

  public async listServerless<T = unknown>(params?: {
    projectId?: string;
  }): Promise<T[]> {
    const query = params?.projectId
      ? `?projectId=${encodeURIComponent(params.projectId)}`
      : '';
    const response = await this.client.request(
      'GET',
      `/api/v1/compute/serverless${query}`
    );
    return response.json();
  }

  public async deployServerless<T = unknown>(
    config: DeployServerlessConfig
  ): Promise<T> {
    const response = await this.client.request(
      'POST',
      '/api/v1/compute/serverless',
      {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
      }
    );
    return response.json();
  }

  public async listLogs<T = unknown>(params?: {
    resourceId?: string;
    resourceType?: string;
    limit?: number;
    since?: string;
  }): Promise<T[]> {
    const searchParams = new URLSearchParams();
    if (params?.resourceId)
      searchParams.set('resourceId', params.resourceId);
    if (params?.resourceType)
      searchParams.set('resourceType', params.resourceType);
    if (params?.limit) searchParams.set('limit', String(params.limit));
    if (params?.since) searchParams.set('since', params.since);
    const query = searchParams.toString();
    const response = await this.client.request(
      'GET',
      `/api/v1/compute/logs${query ? '?' + query : ''}`
    );
    return response.json();
  }

  public async listPods<T = unknown>(params?: {
    deploymentId?: string;
  }): Promise<T[]> {
    const query = params?.deploymentId
      ? `?deploymentId=${encodeURIComponent(params.deploymentId)}`
      : '';
    const response = await this.client.request(
      'GET',
      `/api/v1/compute/pods${query}`
    );
    return response.json();
  }
}
