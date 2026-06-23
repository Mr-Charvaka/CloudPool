package client

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"time"
)

type TokenManager struct {
	client     *CloudPoolClient
	refreshURL string
	onRefresh  func(token string)
}

type refreshResponse struct {
	Token        string `json:"token"`
	RefreshToken string `json:"refreshToken"`
}

func NewTokenManager(client *CloudPoolClient, refreshURL string, onRefresh func(token string)) *TokenManager {
	return &TokenManager{
		client:     client,
		refreshURL: refreshURL,
		onRefresh:  onRefresh,
	}
}

func (tm *TokenManager) EnsureValid(ctx context.Context) error {
	if tm.client.jwtToken == "" {
		return fmt.Errorf("not authenticated: no JWT token available")
	}
	parts, err := decodeJWT(tm.client.jwtToken)
	if err != nil {
		return tm.refresh(ctx)
	}
	exp, ok := parts["exp"].(float64)
	if !ok {
		return tm.refresh(ctx)
	}
	if time.Now().Unix() > int64(exp)-300 {
		return tm.refresh(ctx)
	}
	return nil
}

func (tm *TokenManager) refresh(ctx context.Context) error {
	data, err := tm.client.Post(ctx, tm.refreshURL, map[string]string{
		"refreshToken": tm.client.jwtToken,
	}, nil)
	if err != nil {
		return fmt.Errorf("token refresh failed: %w", err)
	}
	var resp refreshResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return fmt.Errorf("parse refresh response: %w", err)
	}
	tm.client.jwtToken = resp.Token
	if tm.onRefresh != nil {
		tm.onRefresh(resp.Token)
	}
	return nil
}

func decodeJWT(token string) (map[string]interface{}, error) {
	parts := splitN(token, ".", 3)
	if len(parts) < 2 {
		return nil, fmt.Errorf("invalid JWT format")
	}
	decoded, err := base64Decode(parts[1])
	if err != nil {
		return nil, fmt.Errorf("decode JWT payload: %w", err)
	}
	var claims map[string]interface{}
	if err := json.Unmarshal(decoded, &claims); err != nil {
		return nil, fmt.Errorf("parse JWT claims: %w", err)
	}
	return claims, nil
}

func splitN(s, sep string, n int) []string {
	var result []string
	for i := 0; i < n-1; i++ {
		idx := indexOf(s, sep)
		if idx < 0 {
			break
		}
		result = append(result, s[:idx])
		s = s[idx+len(sep):]
	}
	result = append(result, s)
	return result
}

func indexOf(s, sep string) int {
	for i := 0; i <= len(s)-len(sep); i++ {
		if s[i:i+len(sep)] == sep {
			return i
		}
	}
	return -1
}

func base64Decode(s string) ([]byte, error) {
	return base64.RawURLEncoding.DecodeString(s)
}
