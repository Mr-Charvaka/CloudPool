const API_BASE = '/api';

function getToken(): string | null {
  return localStorage.getItem('cp_token');
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {}),
  };

  const token = getToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const csrfCookie = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));
  if (csrfCookie) {
    headers['X-XSRF-TOKEN'] = csrfCookie.split('=')[1];
  }

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers, credentials: 'include' });

  if (res.status === 401) {
    localStorage.removeItem('cp_token');
    window.location.reload();
    throw new Error('Unauthorized');
  }

  if (!res.ok) {
    const body = await res.text();
    throw new Error(`HTTP ${res.status}: ${body}`);
  }

  const contentType = res.headers.get('content-type');
  if (contentType?.includes('application/json')) {
    return res.json();
  }
  return res as unknown as T;
}

export interface FileMetadata {
  id: string;
  bucket: { id: string; name: string };
  name: string;
  originalName: string;
  size: number;
  mimeType: string;
  extension: string;
  driveLocation: string;
  isPublic: boolean;
  isEncrypted: boolean;
  checksum: string;
  createdAt: string;
  updatedAt: string;
}

export interface Bucket {
  id: string;
  name: string;
  description: string;
  isPublic: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Quota {
  limit: number;
  usage: number;
}

export async function fetchFiles(): Promise<FileMetadata[]> {
  return request<FileMetadata[]>('/files');
}

export async function fetchBuckets(): Promise<Bucket[]> {
  return request<Bucket[]>('/files/buckets');
}

export async function fetchQuota(): Promise<Quota> {
  return request<Quota>('/files/quota');
}

export async function uploadFile(file: File, bucket: string): Promise<FileMetadata> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('bucket', bucket);

  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}/files/upload`, {
    method: 'POST',
    headers,
    credentials: 'include',
    body: formData,
  });

  if (res.status === 401) {
    localStorage.removeItem('cp_token');
    window.location.reload();
    throw new Error('Unauthorized');
  }

  if (!res.ok) {
    const body = await res.text();
    throw new Error(`HTTP ${res.status}: ${body}`);
  }

  return res.json();
}

export async function downloadFile(id: string): Promise<void> {
  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}/files/download/${id}`, { headers, credentials: 'include' });

  if (!res.ok) {
    throw new Error(`Download failed: HTTP ${res.status}`);
  }

  const blob = await res.blob();
  const disposition = res.headers.get('content-disposition') || '';
  const match = disposition.match(/filename="?(.+?)"?$/);
  const filename = match ? match[1] : 'download';

  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export async function shareFile(fileId: string, email?: string, expiryHours?: number): Promise<{ token: string }> {
  const body: Record<string, unknown> = {};
  if (email) body.sharedWithEmail = email;
  if (expiryHours) body.expiryHours = expiryHours;

  return request(`/files/${fileId}/share`, {
    method: 'POST',
    body: JSON.stringify(Object.keys(body).length ? body : undefined),
  });
}

export async function fetchLogs(): Promise<Array<{ id: string; action: string; details: string; timestamp: string }>> {
  return request('/files/logs');
}

// ── Topology (Secrets & Snapshots) ──

export interface Secret {
  id: string;
  secretKey: string;
  secretValue?: string;
}

export interface Snapshot {
  id: string;
  name: string;
  createdAt: string;
}

export async function fetchSecrets(): Promise<Secret[]> {
  return request('/v1/projects/current/secrets');
}

export async function saveSecret(key: string, value: string): Promise<void> {
  await request('/v1/projects/current/secrets', {
    method: 'POST',
    body: JSON.stringify({ key, value }),
  });
}

export async function deleteSecret(id: string): Promise<void> {
  await request(`/v1/projects/secrets/${id}`, { method: 'DELETE' });
}

export async function fetchSnapshots(): Promise<Snapshot[]> {
  return request('/v1/projects/current/snapshots');
}

export async function createSnapshot(name: string): Promise<void> {
  await request('/v1/projects/current/snapshots', {
    method: 'POST',
    body: JSON.stringify({ name }),
  });
}

export async function restoreSnapshot(id: string): Promise<void> {
  await request(`/v1/projects/current/snapshots/${id}/restore`, { method: 'POST' });
}

export async function deleteSnapshot(id: string): Promise<void> {
  await request(`/v1/projects/snapshots/${id}`, { method: 'DELETE' });
}

// ── Vector Search ──

export interface VectorResult {
  id: string;
  name: string;
  score: number;
  pool: string;
  size: number;
  type: string;
}

export async function vectorSearch(query: string): Promise<VectorResult[]> {
  return request(`/vector/search?q=${encodeURIComponent(query)}`);
}

// ── API Keys ──

export interface ApiKey {
  id: string;
  name: string;
  keyHash: string;
  active: boolean;
  expiresAt: string;
}

export async function fetchApiKeys(): Promise<ApiKey[]> {
  return request('/keys');
}

export async function generateApiKey(name: string): Promise<{ apiKey: string }> {
  return request('/keys/generate', {
    method: 'POST',
    body: JSON.stringify({ name, description: 'Dashboard generated key', daysToLive: 30 }),
  });
}

export async function deleteApiKey(id: string): Promise<void> {
  await request(`/keys/${id}`, { method: 'DELETE' });
}

// ── Analytics ──

export interface AnalyticsSummary {
  totalRequests: number;
  averageLatencyMs: number;
  successRate: number;
  errorCount: number;
  statusDistribution: Record<string, number>;
  topPaths: Array<{ path: string; count: number }>;
}

export interface AnalyticsLog {
  id: string;
  requestMethod: string;
  requestPath: string;
  statusCode: number;
  durationMs: number;
  ipAddress: string;
  timestamp: string;
}

export async function fetchAnalyticsSummary(): Promise<AnalyticsSummary> {
  return request('/analytics/summary');
}

export async function fetchAnalyticsLogs(): Promise<AnalyticsLog[]> {
  return request('/analytics/logs');
}

// ── Compute (Static, Serverless, Container) ──

export interface StaticSite {
  id: string;
  name: string;
  bucketName: string;
  domain: string;
  status: string;
}

export async function fetchStaticSites(): Promise<StaticSite[]> {
  return request('/compute/static');
}

export async function deployStaticSite(name: string, bucketName: string, domain: string): Promise<void> {
  await request('/compute/static', {
    method: 'POST',
    body: JSON.stringify({ name, bucketName, domain }),
  });
}

export async function deleteStaticSite(id: string): Promise<void> {
  await request(`/compute/static/${id}`, { method: 'DELETE' });
}

export interface ServerlessFunction {
  id: string;
  name: string;
  triggerRoute: string;
}

export async function fetchServerlessFunctions(): Promise<ServerlessFunction[]> {
  return request('/compute/serverless');
}

export async function deployServerlessFunction(name: string, triggerRoute: string, code: string): Promise<void> {
  await request('/compute/serverless', {
    method: 'POST',
    body: JSON.stringify({ name, triggerRoute, code }),
  });
}

export async function deleteServerlessFunction(id: string): Promise<void> {
  await request(`/compute/serverless/${id}`, { method: 'DELETE' });
}

export async function executeServerlessFunction(id: string, params: Record<string, unknown>): Promise<{ executionOutput: string; timestamp: string }> {
  return request(`/compute/serverless/${id}/execute`, {
    method: 'POST',
    body: JSON.stringify(params),
  });
}

export interface ContainerDeployment {
  id: string;
  name: string;
  dockerImage: string;
  replicas: number;
  cpu: number;
  memory: number;
  status: string;
}

export async function fetchContainers(): Promise<ContainerDeployment[]> {
  return request('/compute/container');
}

export async function deployContainer(name: string, dockerImage: string, replicas: number, cpu: number, memory: number): Promise<void> {
  await request('/compute/container', {
    method: 'POST',
    body: JSON.stringify({ name, dockerImage, replicas, cpu, memory }),
  });
}

export async function deleteContainer(id: string): Promise<void> {
  await request(`/compute/container/${id}`, { method: 'DELETE' });
}

export async function scaleContainer(id: string, replicas: number): Promise<void> {
  await request(`/compute/container/${id}/scale?replicas=${replicas}`, { method: 'POST' });
}

export async function fetchContainerLogs(id: string): Promise<{ logs: string }> {
  return request(`/compute/container/${id}/logs`);
}

// ── Email Sandbox ──

export interface EmailItem {
  id: string;
  toAddress: string;
  fromAddress?: string;
  subject: string;
  body: string;
  status?: string;
  errorMessage?: string;
  sentAt?: string;
  receivedAt?: string;
}

export async function fetchEmailOutbox(): Promise<EmailItem[]> {
  return request('/dev/emails');
}

export async function fetchEmailInbox(): Promise<EmailItem[]> {
  return request('/dev/emails/inbox');
}

export async function clearEmailOutbox(): Promise<void> {
  await request('/dev/emails', { method: 'DELETE' });
}

export async function clearEmailInbox(): Promise<void> {
  await request('/dev/emails/inbox', { method: 'DELETE' });
}

export async function sendTestEmail(to: string, subject: string, body: string): Promise<void> {
  await request('/dev/emails/send-test', {
    method: 'POST',
    body: JSON.stringify({ to, subject, body }),
  });
}
