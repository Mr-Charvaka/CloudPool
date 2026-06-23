package cloudpool

import (
	"testing"
	"time"
)

func TestNewClient(t *testing.T) {
	client := NewClient(ClientConfig{
		BaseURL: "http://localhost:8080/api",
		APIKey:  "test-key",
	})
	if client == nil {
		t.Fatal("NewClient() returned nil")
	}
	if client.retryMax != 3 {
		t.Errorf("expected 3 retries, got %d", client.retryMax)
	}
}

func TestClientOptions(t *testing.T) {
	client := NewClient(ClientConfig{BaseURL: "http://localhost:8080/api"})
	client.Use(WithRetryMax(5), WithTimeout(10*time.Second))
	if client.retryMax != 5 {
		t.Errorf("expected 5, got %d", client.retryMax)
	}
	if client.httpClient.Timeout != 10*time.Second {
		t.Errorf("expected 10s timeout, got %v", client.httpClient.Timeout)
	}
}

func TestClientServices(t *testing.T) {
	client := NewClient(ClientConfig{BaseURL: "http://localhost:8080/api", APIKey: "test-key"})
	if client.Auth == nil {
		t.Error("Auth service is nil")
	}
	if client.Files == nil {
		t.Error("Files service is nil")
	}
	if client.Database == nil {
		t.Error("Database service is nil")
	}
	if client.Vector == nil {
		t.Error("Vector service is nil")
	}
	if client.Compute == nil {
		t.Error("Compute service is nil")
	}
	if client.Network == nil {
		t.Error("Network service is nil")
	}
	if client.Payments == nil {
		t.Error("Payments service is nil")
	}
	if client.KV == nil {
		t.Error("KV service is nil")
	}
	if client.Emails == nil {
		t.Error("Emails service is nil")
	}
}
