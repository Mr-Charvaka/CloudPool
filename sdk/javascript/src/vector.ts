import type { CloudPool } from './client';
import type { VectorCollection, VectorDocument, VectorSearchResult } from './types';

export class VectorClient {
  constructor(private readonly cp: CloudPool) {}

  /** List all vector collections */
  async listCollections(): Promise<VectorCollection[]> {
    return this.cp.request<VectorCollection[]>('GET', '/api/vector/collections');
  }

  /** Create a new vector collection */
  async createCollection(
    name: string,
    description: string,
    dimension = 1536,
    distanceMetric = 'cosine',
  ): Promise<VectorCollection> {
    return this.cp.request<VectorCollection>('POST', '/api/vector/collections', {
      name,
      description,
      dimension,
      distanceMetric,
    });
  }

  /** Delete a collection by ID */
  async deleteCollection(collectionId: string): Promise<void> {
    await this.cp.request('DELETE', `/api/vector/collections/${collectionId}`);
  }

  /**
   * Index a document into a collection.
   * The SDK sends the content; CloudPool generates the embedding automatically.
   */
  async indexDocument(
    collectionId: string,
    docId: string,
    content: string,
    metadata?: Record<string, unknown>,
  ): Promise<VectorDocument> {
    return this.cp.request<VectorDocument>('POST', `/api/vector/collections/${collectionId}/documents`, {
      docId,
      content,
      metadata,
    });
  }

  /** Semantic search within a specific collection */
  async searchCollection(
    collectionId: string,
    query: string,
    limit = 10,
  ): Promise<VectorSearchResult[]> {
    return this.cp.request<VectorSearchResult[]>(
      'GET',
      `/api/vector/collections/${collectionId}/search?query=${encodeURIComponent(query)}&limit=${limit}`,
    );
  }

  /** Semantic search across all user files */
  async searchFiles(query: string): Promise<VectorSearchResult[]> {
    return this.cp.request<VectorSearchResult[]>(
      'GET',
      `/api/vector/search?query=${encodeURIComponent(query)}`,
    );
  }
}
