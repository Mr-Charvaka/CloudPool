package cloudpool

import (
	"context"
	"io"
	"strconv"
)

type FilesClient struct {
	client *CloudPoolClient
}

type FileInfo struct {
	ID           string `json:"id"`
	OriginalName string `json:"originalName"`
	Size         int64  `json:"size"`
	MimeType     string `json:"mimeType,omitempty"`
	Checksum     string `json:"checksum,omitempty"`
	CreatedAt    string `json:"createdAt,omitempty"`
}

type ShareResponse struct {
	Token     string `json:"token"`
	ExpiresAt string `json:"expiresAt"`
}

type BucketInfo struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
}

type QuotaInfo struct {
	Limit int64 `json:"limit"`
	Usage int64 `json:"usage"`
}

type AuditLog struct {
	Action    string `json:"action"`
	Details   string `json:"details"`
	Timestamp string `json:"timestamp"`
}

type ShareRequest struct {
	SharedWithEmail string `json:"sharedWithEmail,omitempty"`
	ExpiryHours     int    `json:"expiryHours,omitempty"`
}

func (f *FilesClient) Upload(ctx context.Context, filePath, bucket string) (map[string]interface{}, error) {
	data, err := f.client.Upload(ctx, "/api/files/upload", filePath, bucket)
	if err != nil {
		return nil, err
	}
	var resp map[string]interface{}
	if err := unmarshalResponse(data, &resp); err != nil {
		return nil, err
	}
	return resp, nil
}

func (f *FilesClient) List(ctx context.Context, page, size int) ([]FileInfo, error) {
	var resp []FileInfo
	err := f.client.GetJSON(ctx, "/api/files", map[string]string{
		"page": strconv.Itoa(page), "size": strconv.Itoa(size),
	}, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (f *FilesClient) Download(ctx context.Context, fileID string, writer io.Writer) (string, error) {
	return f.client.Download(ctx, "/api/files/download/"+fileID, writer)
}

func (f *FilesClient) Share(ctx context.Context, fileID string, req ShareRequest) (*ShareResponse, error) {
	var resp ShareResponse
	err := f.client.PostJSON(ctx, "/api/files/"+fileID+"/share", req, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (f *FilesClient) ListBuckets(ctx context.Context) ([]BucketInfo, error) {
	var resp []BucketInfo
	err := f.client.GetJSON(ctx, "/api/files/buckets", nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (f *FilesClient) Quota(ctx context.Context) (*QuotaInfo, error) {
	var resp QuotaInfo
	err := f.client.GetJSON(ctx, "/api/files/quota", nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (f *FilesClient) Logs(ctx context.Context) ([]AuditLog, error) {
	var resp []AuditLog
	err := f.client.GetJSON(ctx, "/api/files/logs", nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}


