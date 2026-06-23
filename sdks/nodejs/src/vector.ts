import { CloudPoolClient } from './index';

export interface VectorSearchFile {
  id: string;
  originalName: string;
  score: number;
}

export interface VectorCollection {
  id: string;
  name: string;
  description?: string;
  dimension: number;
  distanceMetric: string;
}

export interface VectorDocument {
  id: string;
  docId: string;
  content: string;
  metadata?: Array<{ key: string; value: string }>;
}

export interface SemanticSearchResult {
  docId: string;
  content: string;
  score: number;
}

export class VectorClient {
  constructor(private readonly client: CloudPoolClient) {}

  /**
   * Search across uploaded files semantically.
   */
  public async searchFiles<T = unknown>(query: string): Promise<VectorSearchFile[]> {
    const response = await this.client.request('GET', `/api/vector/search?q=${encodeURIComponent(query)}`);
    return response.json();
  }

  /**
   * Create a custom developer vector collection (Weaviate Class).
   */
  public async createCollection<T = unknown>(
    name: string,
    description: string,
    dimension: number,
    distanceMetric: string = 'cosine'
  ): Promise<VectorCollection> {
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

    const variables = { name, description, dimension, distanceMetric };
    const response = await this.client.graphql<{ createCollection: VectorCollection }>(query, variables);
    if (!response.data) {
      throw new Error('GraphQL response missing data for createCollection');
    }
    return response.data.createCollection;
  }

  /**
   * Index a document with content and optional metadata.
   */
  public async indexDocument<T = unknown>(
    collectionId: string,
    docId: string,
    content: string,
    metadata?: Record<string, string>
  ): Promise<VectorDocument> {
    const query = `
      mutation IndexDocument($collectionId: ID!, $docId: String!, $content: String!, $metadata: [KeyValueInput!]) {
        indexDocument(collectionId: $collectionId, docId: $docId, content: $content, metadata: $metadata) {
          id
          docId
          content
          metadata {
            key
            value
          }
        }
      }
    `;

    const gqlMetadata = metadata
      ? Object.entries(metadata).map(([key, value]) => ({ key, value: String(value) }))
      : null;

    const variables = { collectionId, docId, content, metadata: gqlMetadata };
    const response = await this.client.graphql<{ indexDocument: VectorDocument }>(query, variables);
    if (!response.data) {
      throw new Error('GraphQL response missing data for indexDocument');
    }
    return response.data.indexDocument;
  }

  /**
   * Perform a semantic search on a specific developer vector collection.
   */
  public async search<T = unknown>(
    collectionId: string,
    queryText: string,
    limit: number = 10
  ): Promise<SemanticSearchResult[]> {
    const query = `
      query SemanticSearch($collectionId: ID!, $query: String!, $limit: Int) {
        semanticSearch(collectionId: $collectionId, query: $query, limit: $limit) {
          docId
          content
          score
        }
      }
    `;

    const variables = { collectionId, query: queryText, limit };
    const response = await this.client.graphql<{ semanticSearch: SemanticSearchResult[] }>(query, variables);
    if (!response.data) {
      throw new Error('GraphQL response missing data for semanticSearch');
    }
    return response.data.semanticSearch;
  }
}
