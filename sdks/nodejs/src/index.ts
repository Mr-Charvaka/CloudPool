import { FilesClient } from './files';
import { DatabaseClient } from './database';
import { VectorClient } from './vector';
import { AuthClient } from './auth';
import { ComputeClient } from './compute';
import { NetworkClient } from './network';
import { PaymentsClient } from './payments';
import { KvClient } from './kv';
import { EmailsClient } from './emails';

export interface ClientConfig {
  baseUrl?: string;
  apiKey?: string;
  jwtToken?: string;
  timeout?: number;
}

export class CloudPoolError extends Error {
  public readonly statusCode: number;
  public readonly code: string;
  public readonly details?: unknown;

  constructor(message: string, statusCode: number, code?: string, details?: unknown) {
    super(message);
    this.name = 'CloudPoolError';
    this.statusCode = statusCode;
    this.code = code || 'API_ERROR';
    this.details = details;
  }
}

export class AuthenticationError extends CloudPoolError {
  constructor(message: string, details?: unknown) {
    super(message, 401, 'AUTHENTICATION_ERROR', details);
    this.name = 'AuthenticationError';
  }
}

export class NotFoundError extends CloudPoolError {
  constructor(message: string, details?: unknown) {
    super(message, 404, 'NOT_FOUND', details);
    this.name = 'NotFoundError';
  }
}

export class RateLimitError extends CloudPoolError {
  public readonly retryAfter: number;

  constructor(message: string, retryAfter: number = 0, details?: unknown) {
    super(message, 429, 'RATE_LIMITED', details);
    this.name = 'RateLimitError';
    this.retryAfter = retryAfter;
  }
}

export class ValidationError extends CloudPoolError {
  constructor(message: string, details?: unknown) {
    super(message, 422, 'VALIDATION_ERROR', details);
    this.name = 'ValidationError';
  }
}

export class ConflictError extends CloudPoolError {
  constructor(message: string, details?: unknown) {
    super(message, 409, 'CONFLICT', details);
    this.name = 'ConflictError';
  }
}

export class GraphQLError extends CloudPoolError {
  public readonly graphqlErrors: Array<{
    message: string;
    locations?: unknown[];
    path?: string[];
    extensions?: unknown;
  }>;

  constructor(
    errors: Array<{
      message: string;
      locations?: unknown[];
      path?: string[];
      extensions?: unknown;
    }>
  ) {
    if (!errors || errors.length === 0) {
      super('GraphQL returned an empty errors array', 400, 'GRAPHQL_EMPTY_ERRORS', errors);
      this.graphqlErrors = errors || [];
    } else {
      super(errors[0].message || 'GraphQL Error', 400, 'GRAPHQL_ERROR', errors);
      this.graphqlErrors = errors;
    }
    this.name = 'GraphQLError';
  }
}

export interface GraphQLResponse<T = unknown> {
  data?: T;
  errors?: Array<{
    message: string;
    locations?: unknown[];
    path?: string[];
    extensions?: unknown;
  }>;
}

const MAX_RETRIES = 3;
const BASE_RETRY_DELAY_MS = 1000;
const MAX_RETRY_DELAY_MS = 120_000;

export class CloudPoolClient {
  public readonly baseUrl: string;
  public readonly timeout: number;
  private readonly apiKey?: string;
  private readonly jwtToken?: string;

  public readonly auth: AuthClient;
  public readonly files: FilesClient;
  public readonly database: DatabaseClient;
  public readonly vector: VectorClient;
  public readonly compute: ComputeClient;
  public readonly network: NetworkClient;
  public readonly payments: PaymentsClient;
  public readonly kv: KvClient;
  public readonly emails: EmailsClient;

  constructor(config: ClientConfig = {}) {
    this.baseUrl = (
      config.baseUrl || 'http://localhost:8080'
    ).replace(/\/$/, '');
    this.apiKey = config.apiKey;
    this.jwtToken = config.jwtToken;
    this.timeout = config.timeout || 30_000;

    this.auth = new AuthClient(this);
    this.files = new FilesClient(this);
    this.database = new DatabaseClient(this);
    this.vector = new VectorClient(this);
    this.compute = new ComputeClient(this);
    this.network = new NetworkClient(this);
    this.payments = new PaymentsClient(this);
    this.kv = new KvClient(this);
    this.emails = new EmailsClient(this);
  }

  public async request<T = unknown>(
    method: string,
    path: string,
    options: RequestInit = {}
  ): Promise<Response> {
    const url = `${this.baseUrl}${path.startsWith('/') ? path : '/' + path}`;

    const headers = new Headers(options.headers || {});

    if (this.apiKey) {
      headers.set('X-API-KEY', this.apiKey);
    } else if (this.jwtToken) {
      headers.set('Authorization', `Bearer ${this.jwtToken}`);
    }

    let lastError: CloudPoolError | Error | null = null;

    for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), this.timeout);

      try {
        const response = await fetch(url, {
          ...options,
          method,
          headers,
          signal: controller.signal,
        });

        clearTimeout(timeoutId);

        if (response.ok) {
          return response;
        }

        const errorBody = await this.parseErrorBody(response);

        if (this.shouldRetry(response.status)) {
          const retryAfterMs = this.parseRetryAfter(response);
          lastError = this.createTypedError(
            response.status,
            errorBody.message,
            errorBody.code,
            errorBody.details,
            retryAfterMs
          );
          await this.sleep(attempt, retryAfterMs);
          continue;
        }

        throw this.createTypedError(
          response.status,
          errorBody.message,
          errorBody.code,
          errorBody.details
        );
      } catch (err) {
        clearTimeout(timeoutId);

        if (err instanceof CloudPoolError) {
          throw err;
        }

        if (this.isNetworkOrTimeoutError(err)) {
          if (attempt < MAX_RETRIES) {
            lastError = err as Error;
            await this.sleep(attempt);
            continue;
          }
          throw new CloudPoolError(
            `Request failed after ${MAX_RETRIES + 1} attempts: ${(err as Error).message}`,
            0,
            'NETWORK_ERROR',
            err
          );
        }

        throw new CloudPoolError(
          `Unexpected request error: ${(err as Error).message || String(err)}`,
          0,
          'UNKNOWN_ERROR',
          err
        );
      }
    }

    throw (
      lastError ||
      new CloudPoolError(
        'Request failed after max retries',
        0,
        'MAX_RETRIES_EXCEEDED'
      )
    );
  }

  public async graphql<T = unknown>(
    query: string,
    variables?: Record<string, unknown>
  ): Promise<GraphQLResponse<T>> {
    const response = await this.request('POST', '/graphql', {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query, variables }),
    });

    const contentType = response.headers.get('content-type') || '';
    if (!contentType.includes('application/json')) {
      const text = await response.text();
      throw new CloudPoolError(
        `GraphQL returned non-JSON response: ${text.slice(0, 200)}`,
        502,
        'INVALID_RESPONSE'
      );
    }

    const json = (await response.json()) as GraphQLResponse<T>;

    if (json.errors) {
      throw new GraphQLError(json.errors);
    }

    return json;
  }

  private async parseErrorBody(
    response: Response
  ): Promise<{ message: string; code?: string; details?: unknown }> {
    try {
      if (response.headers.get('content-type')?.includes('application/json')) {
        const json = await response.json();
        if (json.error) {
          return {
            message: json.error.message || response.statusText,
            code: json.error.code,
            details: json.error.details,
          };
        }
        return {
          message: json.message || response.statusText,
          code: json.code,
        };
      }
      const text = await response.text();
      return { message: text || response.statusText };
    } catch {
      return { message: response.statusText };
    }
  }

  private createTypedError(
    status: number,
    message: string,
    code?: string,
    details?: unknown,
    retryAfterMs: number = 0
  ): CloudPoolError {
    switch (status) {
      case 401:
      case 403:
        return new AuthenticationError(message, details);
      case 404:
        return new NotFoundError(message, details);
      case 409:
        return new ConflictError(message, details);
      case 422:
        return new ValidationError(message, details);
      case 429:
        return new RateLimitError(
          message,
          Math.ceil(retryAfterMs / 1000),
          details
        );
      default:
        return new CloudPoolError(message, status, code, details);
    }
  }

  private shouldRetry(status: number): boolean {
    return status === 429 || status >= 500;
  }

  private parseRetryAfter(response: Response): number {
    const header = response.headers.get('Retry-After');
    if (!header) return 0;
    const seconds = parseInt(header, 10);
    if (isNaN(seconds)) return 0;
    return Math.min(seconds * 1000, MAX_RETRY_DELAY_MS);
  }

  private isNetworkOrTimeoutError(err: unknown): boolean {
    if (err instanceof TypeError) return true;
    if (err instanceof Error) {
      const e = err as Error & { code?: string };
      return (
        e.name === 'AbortError' ||
        e.name === 'TimeoutError' ||
        e.name === 'FetchError' ||
        e.code === 'ECONNREFUSED' ||
        e.code === 'ECONNRESET' ||
        e.code === 'ENOTFOUND'
      );
    }
    return false;
  }

  private async sleep(
    attempt: number,
    retryAfterMs: number = 0
  ): Promise<void> {
    if (retryAfterMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, retryAfterMs));
      return;
    }
    const exponential = Math.min(
      BASE_RETRY_DELAY_MS * Math.pow(2, attempt),
      MAX_RETRY_DELAY_MS
    );
    const jitter = Math.random() * exponential;
    await new Promise((resolve) => setTimeout(resolve, jitter));
  }
}
