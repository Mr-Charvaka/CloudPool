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
  public async createTable(
    name: string,
    displayName: string,
    description: string,
    fields: FieldDefinition[],
    projectId?: string
  ): Promise<any> {
    const body: Record<string, any> = {
      name,
      displayName,
      description,
      fields,
    };
    if (projectId) {
      body.projectId = projectId;
    }

    const response = await this.client.request('POST', 'v1/db/tables', {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    return response.json();
  }

  /**
   * List all custom/dynamic database tables.
   */
  public async listTables(projectId?: string): Promise<any[]> {
    const path = projectId ? `v1/db/tables?projectId=${encodeURIComponent(projectId)}` : 'v1/db/tables';
    const response = await this.client.request('GET', path);
    return response.json();
  }

  /**
   * Get specific custom table definition metadata by ID.
   */
  public async getTable(tableId: string): Promise<any> {
    const response = await this.client.request('GET', `v1/db/tables/${tableId}`);
    return response.json();
  }

  /**
   * Delete custom relational table and drops database structure.
   */
  public async deleteTable(tableId: string): Promise<any> {
    const response = await this.client.request('DELETE', `v1/db/tables/${tableId}`);
    return response.json();
  }

  /**
   * Insert record row into a dynamic custom database table.
   */
  public async insertRecord(tableId: string, record: Record<string, any>): Promise<any> {
    const response = await this.client.request('POST', `v1/db/tables/${tableId}/records`, {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(record),
    });
    return response.json();
  }

  /**
   * Query and list all records inside a custom relational database table.
   */
  public async queryRecords(tableId: string): Promise<any[]> {
    const response = await this.client.request('GET', `v1/db/tables/${tableId}/records`);
    return response.json();
  }
}
