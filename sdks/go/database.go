package cloudpool

import "context"

type DatabaseClient struct {
	client *CloudPoolClient
}

type FieldDefinition struct {
	FieldName string `json:"fieldName"`
	FieldType string `json:"fieldType"`
	Required  bool   `json:"required"`
}

type CreateTableRequest struct {
	Name        string            `json:"name"`
	DisplayName string            `json:"displayName,omitempty"`
	Description string            `json:"description,omitempty"`
	Fields      []FieldDefinition `json:"fields"`
	ProjectID   string            `json:"projectId,omitempty"`
}

type TableInfo struct {
	Name     string `json:"name"`
	RowCount int    `json:"rowCount,omitempty"`
	ID       string `json:"id,omitempty"`
}

type BackupResponse struct {
	URL string `json:"url"`
}

func (db *DatabaseClient) CreateTable(ctx context.Context, req CreateTableRequest) (map[string]interface{}, error) {
	var resp map[string]interface{}
	err := db.client.PostJSON(ctx, "/api/db/tables", req, nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (db *DatabaseClient) ListTables(ctx context.Context, projectID string) ([]TableInfo, error) {
	path := "/api/db/tables"
	if projectID != "" {
		path += "?projectId=" + projectID
	}
	var resp []TableInfo
	err := db.client.GetJSON(ctx, path, nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (db *DatabaseClient) InsertRecord(ctx context.Context, tableID string, record map[string]interface{}) (map[string]interface{}, error) {
	var resp map[string]interface{}
	err := db.client.PostJSON(ctx, "/api/db/"+tableID+"/records", record, nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (db *DatabaseClient) QueryRecords(ctx context.Context, tableID string) ([]map[string]interface{}, error) {
	var resp []map[string]interface{}
	err := db.client.GetJSON(ctx, "/api/db/"+tableID+"/records", nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (db *DatabaseClient) QueryFiltered(ctx context.Context, tableID, field, value string) ([]map[string]interface{}, error) {
	var resp []map[string]interface{}
	err := db.client.GetJSON(ctx, "/api/db/"+tableID+"/query", map[string]string{
		"field": field, "value": value,
	}, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (db *DatabaseClient) Backup(ctx context.Context, tableID string) (*BackupResponse, error) {
	var resp BackupResponse
	err := db.client.PostJSON(ctx, "/api/db/"+tableID+"/backup", nil, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}
