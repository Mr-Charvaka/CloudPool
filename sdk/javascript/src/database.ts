import type { CloudPool } from './client';
import type { DevTable, QueryResult } from './types';

export class DatabaseClient {
  constructor(private readonly cp: CloudPool) {}

  /** List all dev tables in a project */
  async listTables(projectId: string): Promise<DevTable[]> {
    return this.cp.request<DevTable[]>('GET', `/api/database/tables?projectId=${projectId}`);
  }

  /** Create a new table with dynamic schema */
  async createTable(
    projectId: string,
    name: string,
    displayName: string,
    fields: Array<{ name: string; fieldType: string; required?: boolean }>,
  ): Promise<DevTable> {
    return this.cp.request<DevTable>('POST', '/api/database/tables', {
      projectId,
      name,
      displayName,
      fields,
    });
  }

  /** Delete a table by ID */
  async deleteTable(tableId: string): Promise<void> {
    await this.cp.request('DELETE', `/api/database/tables/${tableId}`);
  }

  /**
   * Execute a raw SQL query on the database console.
   * Supports SELECT, INSERT, UPDATE, DELETE.
   */
  async query(sql: string, connectionId?: string): Promise<QueryResult> {
    return this.cp.request<QueryResult>('POST', '/api/console/query', { sql, connectionId });
  }

  /** Insert a row into a dev table */
  async insert(tableId: string, data: Record<string, unknown>): Promise<Record<string, unknown>> {
    return this.cp.request('POST', `/api/database/tables/${tableId}/rows`, data);
  }

  /** Get all rows in a dev table */
  async getRows(tableId: string): Promise<Record<string, unknown>[]> {
    return this.cp.request<Record<string, unknown>[]>('GET', `/api/database/tables/${tableId}/rows`);
  }

  /** Delete a row by ID */
  async deleteRow(tableId: string, rowId: string): Promise<void> {
    await this.cp.request('DELETE', `/api/database/tables/${tableId}/rows/${rowId}`);
  }
}
