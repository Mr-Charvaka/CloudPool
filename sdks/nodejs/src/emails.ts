import { CloudPoolClient } from './index';

export interface SendEmailParams {
  to: string | string[];
  subject: string;
  body: string;
  from?: string;
  cc?: string | string[];
  bcc?: string | string[];
  contentType?: 'text/plain' | 'text/html';
}

export class EmailsClient {
  constructor(private readonly client: CloudPoolClient) {}

  public async send<T = unknown>(params: SendEmailParams): Promise<T> {
    const response = await this.client.request(
      'POST',
      '/api/v1/emails/send',
      {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params),
      }
    );
    return response.json();
  }

  public async listInbox<T = unknown>(params?: {
    limit?: number;
    unreadOnly?: boolean;
  }): Promise<T[]> {
    const searchParams = new URLSearchParams();
    if (params?.limit) searchParams.set('limit', String(params.limit));
    if (params?.unreadOnly)
      searchParams.set('unreadOnly', 'true');
    const query = searchParams.toString();
    const response = await this.client.request(
      'GET',
      `/api/v1/emails/inbox${query ? '?' + query : ''}`
    );
    return response.json();
  }
}
