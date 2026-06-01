// ── CloudPool SDK Type Definitions ──────────────────────────────────────

export interface CloudPoolConfig {
  /** Base URL of your CloudPool instance e.g. http://localhost:8080 */
  baseUrl: string;
  /** API key for authentication (preferred for server-side use) */
  apiKey?: string;
  /** JWT token for authentication (preferred for client-side use) */
  token?: string;
}

// ── Auth ─────────────────────────────────────────────────────────────────

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface User {
  id: string;
  email: string;
  fullName: string;
  createdAt: string;
}

// ── Projects ─────────────────────────────────────────────────────────────

export interface Project {
  id: string;
  name: string;
  description?: string;
  userId: string;
  createdAt: string;
  updatedAt: string;
}

// ── Storage ──────────────────────────────────────────────────────────────

export interface Bucket {
  id: string;
  name: string;
  userId: string;
  createdAt: string;
}

export interface FileMetadata {
  id: string;
  originalName: string;
  mimeType: string;
  size: number;
  extension?: string;
  driveLocation?: string;
  shareToken?: string;
  expiresAt?: string;
  createdAt: string;
}

export interface UploadResult extends FileMetadata {
  bucket: Bucket;
}

// ── Database ─────────────────────────────────────────────────────────────

export interface DatabaseConnection {
  id: string;
  dbType: 'POSTGRESQL' | 'H2' | 'REDIS';
  host: string;
  port: number;
  databaseName: string;
  username: string;
  active: boolean;
  projectId: string;
}

export interface QueryResult {
  columns: string[];
  rows: Record<string, unknown>[];
  rowCount: number;
  executionTimeMs: number;
}

export interface DevTable {
  id: string;
  name: string;
  displayName: string;
  description?: string;
  projectId: string;
  createdAt: string;
  fields: DevTableField[];
}

export interface DevTableField {
  id: string;
  name: string;
  fieldType: 'VARCHAR' | 'INTEGER' | 'BOOLEAN' | 'DOUBLE' | 'TEXT';
  required: boolean;
}

// ── Vector Search ─────────────────────────────────────────────────────────

export interface VectorCollection {
  id: string;
  name: string;
  description?: string;
  dimension: number;
  distanceMetric: string;
  createdAt: string;
}

export interface VectorDocument {
  id: string;
  docId: string;
  content: string;
  metadata?: Record<string, unknown>;
  collectionId: string;
}

export interface VectorSearchResult {
  docId: string;
  content: string;
  score: number;
  metadata?: Record<string, unknown>;
}

export interface FileSearchResult {
  id: string;
  name: string;
  pool: string;
  size: number;
  type: string;
  score: number;
}

// ── Compute ───────────────────────────────────────────────────────────────

export interface ContainerDeployment {
  id: string;
  name: string;
  dockerImage: string;
  cpu: number;
  memory: number;
  replicas: number;
  status: 'BUILDING' | 'DEPLOYING' | 'LIVE' | 'FAILED';
  logs?: string;
}

export interface ServerlessFunction {
  id: string;
  name: string;
  triggerRoute: string;
  code: string;
  status: 'ACTIVE' | 'INACTIVE';
}

export interface StaticSite {
  id: string;
  name: string;
  bucketName: string;
  domain?: string;
  status: 'DEPLOYED' | 'FAILED';
}
