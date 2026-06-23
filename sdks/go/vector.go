package cloudpool

import "context"

type VectorClient struct {
	client *CloudPoolClient
}

type VectorSearchRequest struct {
	Query string `json:"query"`
	Limit int    `json:"limit,omitempty"`
}

type VectorSearchResult struct {
	ID    string  `json:"id"`
	Score float64 `json:"score"`
	Text  string  `json:"text,omitempty"`
}

type VectorImportResult struct {
	Imported int `json:"imported"`
}

func (v *VectorClient) Search(ctx context.Context, index, query string, limit int) ([]map[string]interface{}, error) {
	body := map[string]interface{}{"query": query}
	if limit > 0 {
		body["limit"] = limit
	}
	var resp []map[string]interface{}
	err := v.client.PostJSON(ctx, "/api/vector/"+index+"/search", body, nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (v *VectorClient) Import(ctx context.Context, index string, vectors interface{}) (*VectorImportResult, error) {
	var resp VectorImportResult
	err := v.client.PostJSON(ctx, "/api/vector/"+index+"/import", vectors, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (v *VectorClient) Delete(ctx context.Context, index, vectorID string) error {
	_, err := v.client.Delete(ctx, "/api/vector/"+index+"/"+vectorID)
	return err
}

func (v *VectorClient) Schema(ctx context.Context, index string) (map[string]interface{}, error) {
	var resp map[string]interface{}
	err := v.client.GetJSON(ctx, "/api/vector/"+index+"/schema", nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}
