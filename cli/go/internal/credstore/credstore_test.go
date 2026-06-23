package credstore

import (
	"os"
	"path/filepath"
	"testing"
)

func TestFileStoreSetGet(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "creds.json")
	store := New(path)

	if err := store.Set("svc", "user", "secret123"); err != nil {
		t.Fatalf("set failed: %v", err)
	}

	val, err := store.Get("svc", "user")
	if err != nil {
		t.Fatalf("get failed: %v", err)
	}
	if val != "secret123" {
		t.Errorf("expected secret123, got %s", val)
	}
}

func TestFileStoreDelete(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "creds.json")
	store := New(path)

	store.Set("svc", "user", "val")
	if err := store.Delete("svc", "user"); err != nil {
		t.Fatalf("delete failed: %v", err)
	}
	if _, err := store.Get("svc", "user"); err == nil {
		t.Error("expected error after delete")
	}
}

func TestFileStoreNotFound(t *testing.T) {
	store := New("/nonexistent/path/creds.json")
	if _, err := store.Get("svc", "user"); err == nil {
		t.Error("expected error for missing file")
	}
}

func TestFileStorePersistence(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "creds.json")

	s1 := New(path)
	s1.Set("svc", "user", "persisted")

	s2 := New(path)
	val, err := s2.Get("svc", "user")
	if err != nil {
		t.Fatalf("get from second store failed: %v", err)
	}
	if val != "persisted" {
		t.Errorf("expected persisted, got %s", val)
	}
}

func TestStoreWritesJSON(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "creds.json")
	store := New(path)
	store.Set("tokens", "jwt", "token123")

	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read file: %v", err)
	}
	if len(data) == 0 {
		t.Error("expected non-empty credential file")
	}
}
