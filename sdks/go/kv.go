package cloudpool

import "context"

type KVClient struct {
	client *CloudPoolClient
}

type KVEntry struct {
	Key   string `json:"key"`
	Value string `json:"value"`
	TTL   string `json:"ttl,omitempty"`
}

type SetKVRequest struct {
	Value string `json:"value"`
	TTL   int    `json:"ttl,omitempty"`
}

func (k *KVClient) Set(ctx context.Context, key, value string, ttl int) error {
	body := SetKVRequest{Value: value}
	if ttl > 0 {
		body.TTL = ttl
	}
	_, err := k.client.Post(ctx, "/api/kv/"+key, body, nil)
	return err
}

func (k *KVClient) Get(ctx context.Context, key string) (*KVEntry, error) {
	var resp KVEntry
	err := k.client.GetJSON(ctx, "/api/kv/"+key, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (k *KVClient) List(ctx context.Context) ([]KVEntry, error) {
	var resp []KVEntry
	err := k.client.GetJSON(ctx, "/api/kv", nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (k *KVClient) Delete(ctx context.Context, key string) error {
	_, err := k.client.Delete(ctx, "/api/kv/"+key)
	return err
}
