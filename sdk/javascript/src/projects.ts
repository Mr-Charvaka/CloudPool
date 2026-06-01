import type { CloudPool } from './client';
import type { Project } from './types';

export class ProjectClient {
  constructor(private readonly cp: CloudPool) {}

  /** List all projects for the current user */
  async list(): Promise<Project[]> {
    return this.cp.request<Project[]>('GET', '/api/projects');
  }

  /** Create a new project */
  async create(name: string, description?: string): Promise<Project> {
    return this.cp.request<Project>('POST', '/api/projects', { name, description });
  }

  /** Get a specific project by ID */
  async get(projectId: string): Promise<Project> {
    return this.cp.request<Project>('GET', `/api/projects/${projectId}`);
  }

  /** Delete a project and all associated resources */
  async delete(projectId: string): Promise<void> {
    await this.cp.request('DELETE', `/api/projects/${projectId}`);
  }

  /** Take a snapshot of the project infrastructure state */
  async snapshot(projectId: string, label?: string): Promise<{ snapshotId: string; createdAt: string }> {
    return this.cp.request('POST', `/api/projects/${projectId}/snapshot`, { label });
  }

  /** Restore a project to a previous snapshot */
  async restore(projectId: string, snapshotId: string): Promise<void> {
    await this.cp.request('POST', `/api/projects/${projectId}/restore/${snapshotId}`);
  }
}
