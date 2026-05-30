import { CloudPoolClient } from './index';

export class VectorClient {
  constructor(private readonly client: CloudPoolClient) {}

  /**
   * Search across uploaded files semantically.
   */
  public async searchFiles(query: string): Promise<any[]> {
    const response = await this.client.request('GET', `vector/search?q=${encodeURIComponent(query)}`);
    return response.json();
  }

  /**
   * Create a custom developer vector collection (Weaviate Class).
   */
  public async createCollection(
    name: string,
    description: string,
    dimension: number,
    distanceMetric: string = 'cosine'
  ): Promise<any> {
    const query = `
      mutation CreateCollection($name: String!, $description: String, $dimension: Int!, $distanceMetric: String) {
        createCollection(name: $name, description: $description, dimension: $dimension, distanceMetric: $distanceMetric) {
          id
          name
          description
          dimension
          distanceMetric
        }
      }
    `;

    const variables = {
      name,
      description,
      dimension,
      distanceMetric,
    };

    const response = await this.client.request('POST', '/graphql', {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query, variables }),
    });

    const json = await response.json();
    if (json.errors) {
      throw new Error(`GraphQL Error: ${JSON.stringify(json.errors)}`);
    }

    return json.data.createCollection;
  }

  /**
   * Index a document with content and optional metadata.
   */
  public async indexDocument(
    collectionId: string,
    docId: string,
    content: string,
    metadata?: Record<string, string>
  ): Promise<any> {
    const query = `
      mutation IndexDocument($collectionId: ID!, $docId: String!, $content: String!, $metadata: [KeyValueInput!]) {
        indexDocument(collectionId: $collectionId, docId: $docId, content: $content, metadata: $metadata) {
          id
          docId
          content
          metadata
        }
      }
    `;

    const gqlMetadata = metadata
      ? Object.entries(metadata).map(([key, value]) => ({ key, value: String(value) }))
      : null;

    const variables = {
      collectionId,
      docId,
      content,
      metadata: gqlMetadata,
    };

    const response = await this.client.request('POST', '/graphql', {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query, variables }),
    });

    const json = await response.json();
    if (json.errors) {
      throw new Error(`GraphQL Error: ${JSON.stringify(json.errors)}`);
    }

    return json.data.indexDocument;
  }

  /**
   * Perform a semantic search on a specific developer vector collection.
   */
  public async search(
    collectionId: string,
    queryText: string,
    limit: number = 10
  ): Promise<any[]> {
    const query = `
      query SemanticSearch($collectionId: ID!, $query: String!, $limit: Int) {
        semanticSearch(collectionId: $collectionId, query: $query, limit: $limit) {
          docId
          content
          score
        }
      }
    `;

    const variables = {
      collectionId,
      query: queryText,
      limit,
    };

    const response = await this.client.request('POST', '/graphql', {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query, variables }),
    });

    const json = await response.json();
    if (json.errors) {
      throw new Error(`GraphQL Error: ${JSON.stringify(json.errors)}`);
    }

    return json.data.semanticSearch;
  }
}
