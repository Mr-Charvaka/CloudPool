package config

import (
	"os"
	"testing"
)

func TestConfigDirEnv(t *testing.T) {
	tmp := t.TempDir()
	t.Setenv("CLOUDPOOL_CONFIG_DIR", tmp)
	dir, err := configDir()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if dir != tmp {
		t.Errorf("expected %s, got %s", tmp, dir)
	}
}

func TestConfigDirDefault(t *testing.T) {
	os.Unsetenv("CLOUDPOOL_CONFIG_DIR")
	dir, err := configDir()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	home, _ := os.UserHomeDir()
	expected := home + string(os.PathSeparator) + ".cloudpool"
	if dir != expected {
		t.Errorf("expected %s, got %s", expected, dir)
	}
}

func TestCredentialsPath(t *testing.T) {
	tmp := t.TempDir()
	cfg := &Config{ConfigDir: tmp}
	expected := tmp + string(os.PathSeparator) + "credentials.json"
	if cfg.CredentialsPath() != expected {
		t.Errorf("expected %s, got %s", expected, cfg.CredentialsPath())
	}
}

func TestSaveAndClearJWT(t *testing.T) {
	tmp := t.TempDir()
	cfg := &Config{ConfigDir: tmp}
	if err := cfg.SaveJWT("test-token"); err != nil {
		t.Fatalf("save failed: %v", err)
	}
	data, err := os.ReadFile(cfg.CredentialsPath())
	if err != nil {
		t.Fatalf("read failed: %v", err)
	}
	if string(data) != `{"jwt_token":"test-token"}` {
		t.Errorf("unexpected content: %s", string(data))
	}
	if err := cfg.ClearAuth(); err != nil {
		t.Fatalf("clear failed: %v", err)
	}
	if _, err := os.Stat(cfg.CredentialsPath()); !os.IsNotExist(err) {
		t.Error("expected credentials file to be deleted")
	}
}
