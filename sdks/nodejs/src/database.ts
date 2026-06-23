import { CloudPoolClient } from './index';

export interface FieldDefinition {
  fieldName: string;
  fieldType: string;
  required: boolean;
}

export class DatabaseClient {
  constructor(private readonly client: CloudPoolClient) {}

  /**
   * Provision a new dynamic relational database table.
   */
  public async createTable<T = unknown>(
    name: string,
    displayName: string,
    description: string,
    fields: FieldDefinition[],
    projectId?: string
  ): Promise<T> {
    const body: Record<string, any> = {
      name,
      displayName,
      description,
      fields,
    };
    if (projectId) {
      body.projectId = projectId;
    }

    const response = await this.client.request('POST', '/api/v1/db/tables', {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    return response.json();
  }

  /**
   * List all custom/dynamic database tables.
   */
  public async listTables<T = unknown>(projectId?: string): Promise<T[]> {
    const path = projectId ? `/api/v1/db/tables?projectId=${encodeURIComponent(projectId)}` : '/api/v1/db/tables';
    const response = await this.client.request('GET', path);
    return response.json();
  }

  /**
   * Get specific custom table definition metadata by ID.
   */
  public async getTable<T = unknown>(tableId: string): Promise<T> {
    const response = await this.client.request('GET', `/api/v1/db/tables/${tableId}`);
    return response.json();
  }

  /**
   * Delete custom relational table and drops database structure.
   */
  public async deleteTable<T = unknown>(tableId: string): Promise<T> {
    const response = await this.client.request('DELETE', `/api/v1/db/tables/${tableId}`);
    return response.json();
  }

  /**
   * Insert record row into a dynamic custom database table.
   */
  public async insertRecord<T = unknown>(tableId: string, record: Record<string, any>): Promise<T> {
    const response = await this.client.request('POST', `/api/v1/db/tables/${tableId}/records`, {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(record),
    });
    return response.json();
  }

  /**
   * Query and list all records inside a custom relational database table.
   */
  public async queryRecords<T = unknown>(tableId: string): Promise<T[]> {
    const response = await this.client.request('GET', `/api/v1/db/tables/${tableId}/records`);
    return response.json();
  }
}
