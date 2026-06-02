/**
 * CloudPool JavaScript/TypeScript SDK
 *
 * Official client for the CloudPool developer infrastructure platform.
 * Supports REST and GraphQL APIs.
 *
 * @example
 * ```ts
 * import { CloudPool } from '@cloudpool/sdk';
 *
 * const cp = new CloudPool({
 *   baseUrl: 'http://localhost:8080',
 *   apiKey: 'cp_your_api_key',
 * });
 *
 * // Upload a file
 * const file = await cp.storage.upload('my-bucket', fileBlob);
 *
 * // Search files semantically
 * const results = await cp.vector.search('find invoices from 2025');
 *
 * // Query your database
 * const rows = await cp.database.query('SELECT * FROM users LIMIT 10');
 * ```
 */

export { CloudPool } from './client';
export { StorageClient } from './storage';
export { DatabaseClient } from './database';
export { VectorClient } from './vector';
export { ProjectClient } from './projects';
export { AuthClient } from './auth';
export * from './types';
