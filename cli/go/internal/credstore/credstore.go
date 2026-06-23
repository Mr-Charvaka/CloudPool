package credstore

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/zalando/go-keyring"
)

type Store interface {
	Get(service, user string) (string, error)
	Set(service, user, password string) error
	Delete(service, user string) error
}

type keyringStore struct{}

func (k *keyringStore) Get(service, user string) (string, error) {
	return keyring.Get(service, user)
}

func (k *keyringStore) Set(service, user, password string) error {
	return keyring.Set(service, user, password)
}

func (k *keyringStore) Delete(service, user string) error {
	return keyring.Delete(service, user)
}

type fileStore struct {
	path string
}

type credFile struct {
	Tokens map[string]string `json:"tokens"`
	Keys   map[string]string `json:"keys"`
}

func (f *fileStore) Get(service, user string) (string, error) {
	c, err := f.read()
	if err != nil {
		return "", err
	}
	m := f.getMap(c)
	if v, ok := m[user]; ok {
		return v, nil
	}
	return "", fmt.Errorf("credential not found")
}

func (f *fileStore) Set(service, user, password string) error {
	c, err := f.read()
	if err != nil {
		c = &credFile{Tokens: map[string]string{}, Keys: map[string]string{}}
	}
	m := f.getMap(c)
	m[user] = password
	return f.write(c)
}

func (f *fileStore) Delete(service, user string) error {
	c, err := f.read()
	if err != nil {
		return nil
	}
	m := f.getMap(c)
	delete(m, user)
	return f.write(c)
}

func (f *fileStore) getMap(c *credFile) map[string]string {
	if c.Tokens == nil {
		c.Tokens = map[string]string{}
	}
	if c.Keys == nil {
		c.Keys = map[string]string{}
	}
	return c.Tokens
}

func (f *fileStore) read() (*credFile, error) {
	data, err := os.ReadFile(f.path)
	if err != nil {
		return nil, err
	}
	var c credFile
	if err := json.Unmarshal(data, &c); err != nil {
		return nil, err
	}
	if c.Tokens == nil {
		c.Tokens = map[string]string{}
	}
	if c.Keys == nil {
		c.Keys = map[string]string{}
	}
	return &c, nil
}

func (f *fileStore) write(c *credFile) error {
	data, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(f.path, data, 0600)
}

func New(path string) Store {
	if path != "" {
		return &fileStore{path: path}
	}
	return &keyringStore{}
}
