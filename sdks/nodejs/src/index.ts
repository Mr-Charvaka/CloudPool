import { FilesClient } from './files';
import { DatabaseClient } from './database';
import { VectorClient } from './vector';

export interface ClientConfig {
  baseUrl?: string;
  apiKey?: string;
  jwtToken?: string;
}

export class CloudPoolClient {
  public readonly baseUrl: string;
  private readonly apiKey?: string;
  private readonly jwtToken?: string;

  public readonly files: FilesClient;
  public readonly database: DatabaseClient;
  public readonly vector: VectorClient;

  constructor(config: ClientConfig = {}) {
    this.baseUrl = (config.baseUrl || 'http://localhost:8080/api').replace(/\/$/, '');
    this.apiKey = config.apiKey;
    this.jwtToken = config.jwtToken;

    this.files = new FilesClient(this);
    this.database = new DatabaseClient(this);
    this.vector = new VectorClient(this);
  }

  /**
   * Helper to perform authenticated HTTP requests.
   */
  public async request(
    method: string,
    path: string,
    options: RequestInit = {}
  ): Promise<Response> {
    // Resolve absolute path from base host root if starting with /
    let url = `${this.baseUrl}/${path.replace(/^\//, '')}`;
    if (path.startsWith('/')) {
      if (this.baseUrl.endsWith('/api')) {
        url = `${this.baseUrl.substring(0, this.baseUrl.length - 4)}${path}`;
      }
    }

    const headers = new Headers(options.headers || {});
    
    if (this.apiKey) {
      headers.set('X-API-KEY', this.apiKey);
    } else if (this.jwtToken) {
      headers.set('Authorization', `Bearer ${this.jwtToken}`);
    }

    const response = await fetch(url, {
      ...options,
      method,
      headers,
    });

    return this.handleResponse(response);
  }

  private async handleResponse(response: Response): Promise<Response> {
    if (!response.ok) {
      let errorMsg = response.statusText;
      let errorCode = 'API_ERROR';
      
      try {
        const json = await response.json();
        if (json.error) {
          errorMsg = json.error.message || errorMsg;
          errorCode = json.error.code || errorCode;
        }
      } catch {
        // Fallback to text body
        try {
          const text = await response.text();
          if (text) errorMsg = text;
        } catch {}
      }

      throw new Error(`CloudPool API Error [${errorCode}]: ${errorMsg}`);
    }

    return response;
  }
}
