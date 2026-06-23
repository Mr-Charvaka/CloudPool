package client

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func setupTestServer(fn func(w http.ResponseWriter, r *http.Request)) *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(fn))
}

func TestClientGet(t *testing.T) {
	server := setupTestServer(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" {
			t.Errorf("expected GET, got %s", r.Method)
		}
		if r.Header.Get("Authorization") != "Bearer test-token" {
			t.Errorf("expected Bearer token")
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"status":"ok"}`))
	})
	defer server.Close()

	client := New(server.URL, WithJWT("test-token"))
	data, err := client.Get(context.Background(), "/test", nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if string(data) != `{"status":"ok"}` {
		t.Errorf("expected response body, got %s", string(data))
	}
}

func TestClientPost(t *testing.T) {
	server := setupTestServer(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"id":"123"}`))
	})
	defer server.Close()

	client := New(server.URL, WithAPIKey("test-key"))
	data, err := client.Post(context.Background(), "/test", map[string]string{"foo": "bar"}, nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if string(data) != `{"id":"123"}` {
		t.Errorf("expected response, got %s", string(data))
	}
}

func TestClientError(t *testing.T) {
	server := setupTestServer(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		w.Write([]byte(`{"error":"invalid token"}`))
	})
	defer server.Close()

	client := New(server.URL)
	_, err := client.Get(context.Background(), "/test", nil)
	if err == nil {
		t.Fatal("expected error, got nil")
	}
	if err.Error() != "[401] invalid token" {
		t.Errorf("unexpected error: %v", err)
	}
}

func TestRetrySuccess(t *testing.T) {
	attempts := 0
	server := setupTestServer(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		if attempts < 2 {
			w.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		w.Write([]byte(`{"status":"ok"}`))
	})
	defer server.Close()

	client := New(server.URL, WithRetryMax(3))
	data, err := client.Get(context.Background(), "/test", nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if attempts != 2 {
		t.Errorf("expected 2 attempts, got %d", attempts)
	}
	if string(data) != `{"status":"ok"}` {
		t.Errorf("unexpected response: %s", string(data))
	}
}

func TestBaseURL(t *testing.T) {
	client := New("https://api.cloudpool.dev/", WithJWT("tok"))
	if client.baseURL != "https://api.cloudpool.dev" {
		t.Errorf("expected trimmed base url, got %s", client.baseURL)
	}
}
