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
