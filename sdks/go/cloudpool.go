package cloudpool

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

type ClientConfig struct {
	BaseURL  string
	APIKey   string
	JWTToken string
}

type CloudPoolClient struct {
	baseURL    string
	apiKey     string
	jwtToken   string
	httpClient *http.Client
	Database   *DatabaseClient
}

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
	DisplayName string            `json:"displayName"`
	Description string            `json:"description"`
	Fields      []FieldDefinition `json:"fields"`
	ProjectID   string            `json:"projectId,omitempty"`
}

func NewClient(config ClientConfig) *CloudPoolClient {
	baseURL := strings.TrimSuffix(config.BaseURL, "/")
	if baseURL == "" {
		baseURL = "http://localhost:8080/api"
	}
	c := &CloudPoolClient{
		baseURL:  baseURL,
		apiKey:   config.APIKey,
		jwtToken: config.JWTToken,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
	c.Database = &DatabaseClient{client: c}
	return c
}

func (c *CloudPoolClient) Request(method, path string, body interface{}, headers map[string]string) ([]byte, error) {
	url := fmt.Sprintf("%s/%s", c.baseURL, strings.TrimPrefix(path, "/"))

	var bodyReader io.Reader
	if body != nil {
		jsonBytes, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("failed to marshal request body: %w", err)
		}
		bodyReader = bytes.NewReader(jsonBytes)
	}

	req, err := http.NewRequest(method, url, bodyReader)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	for k, v := range headers {
		req.Header.Set(k, v)
	}

	if c.apiKey != "" {
		req.Header.Set("X-API-KEY", c.apiKey)
	} else if c.jwtToken != "" {
		req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", c.jwtToken))
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("http request failed: %w", err)
	}
	defer resp.Body.Close()

	respBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("cloudpool api error [%d]: %s", resp.StatusCode, string(respBytes))
	}

	return respBytes, nil
}

func (db *DatabaseClient) CreateTable(req CreateTableRequest) (map[string]interface{}, error) {
	respBytes, err := db.client.Request("POST", "v1/db/tables", req, nil)
	if err != nil {
		return nil, err
	}
	var res map[string]interface{}
	if err := json.Unmarshal(respBytes, &res); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}
	return res, nil
}

func (db *DatabaseClient) ListTables(projectID string) ([]map[string]interface{}, error) {
	path := "v1/db/tables"
	if projectID != "" {
		path = fmt.Sprintf("v1/db/tables?projectId=%s", projectID)
	}
	respBytes, err := db.client.Request("GET", path, nil, nil)
	if err != nil {
		return nil, err
	}
	var res []map[string]interface{}
	if err := json.Unmarshal(respBytes, &res); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}
	return res, nil
}

func (db *DatabaseClient) InsertRecord(tableID string, record map[string]interface{}) (map[string]interface{}, error) {
	path := fmt.Sprintf("v1/db/tables/%s/records", tableID)
	respBytes, err := db.client.Request("POST", path, record, nil)
	if err != nil {
		return nil, err
	}
	var res map[string]interface{}
	if err := json.Unmarshal(respBytes, &res); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}
	return res, nil
}

func (db *DatabaseClient) QueryRecords(tableID string) ([]map[string]interface{}, error) {
	path := fmt.Sprintf("v1/db/tables/%s/records", tableID)
	respBytes, err := db.client.Request("GET", path, nil, nil)
	if err != nil {
		return nil, err
	}
	var res []map[string]interface{}
	if err := json.Unmarshal(respBytes, &res); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}
	return res, nil
}
