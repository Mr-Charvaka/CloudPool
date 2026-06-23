package cloudpool

import "context"

type AuthClient struct {
	client *CloudPoolClient
}

type LoginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type RegisterRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
	Name     string `json:"name"`
}

type AuthResponse struct {
	Token        string `json:"token"`
	RefreshToken string `json:"refreshToken,omitempty"`
}

type UserProfile struct {
	ID        string `json:"id"`
	Email     string `json:"email"`
	Name      string `json:"name"`
	Role      string `json:"role"`
	CreatedAt string `json:"createdAt"`
}

func (a *AuthClient) Login(ctx context.Context, email, password string) (*AuthResponse, error) {
	var resp AuthResponse
	err := a.client.PostJSON(ctx, "/api/auth/login", LoginRequest{Email: email, Password: password}, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (a *AuthClient) Register(ctx context.Context, email, password, name string) (*AuthResponse, error) {
	var resp AuthResponse
	err := a.client.PostJSON(ctx, "/api/auth/register", RegisterRequest{Email: email, Password: password, Name: name}, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (a *AuthClient) Logout(ctx context.Context) error {
	_, err := a.client.Post(ctx, "/api/auth/logout", nil, nil)
	return err
}

func (a *AuthClient) Me(ctx context.Context) (*UserProfile, error) {
	var resp UserProfile
	err := a.client.GetJSON(ctx, "/api/auth/me", nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (a *AuthClient) RefreshToken(ctx context.Context) (*AuthResponse, error) {
	var resp AuthResponse
	err := a.client.PostJSON(ctx, "/api/auth/refresh", nil, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

type APIKey struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	KeyPrefix string `json:"keyPrefix,omitempty"`
	Key       string `json:"key,omitempty"`
	Active    bool   `json:"active"`
	ExpiresAt string `json:"expiresAt,omitempty"`
}

type GenerateKeyRequest struct {
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
	DaysToLive  int    `json:"daysToLive,omitempty"`
}

type KeyAnalytics struct {
	KeyName        string  `json:"keyName"`
	TotalRequests  int     `json:"totalRequests"`
	SuccessCount   int     `json:"successCount"`
	ErrorCount     int     `json:"errorCount"`
	AvgResponseMs  float64 `json:"avgResponseTimeMs"`
}

func (a *AuthClient) ListKeys(ctx context.Context) ([]APIKey, error) {
	var resp []APIKey
	err := a.client.GetJSON(ctx, "/api/keys", nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (a *AuthClient) GenerateKey(ctx context.Context, req GenerateKeyRequest) (*APIKey, error) {
	var resp APIKey
	err := a.client.PostJSON(ctx, "/api/keys/generate", req, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (a *AuthClient) DeleteKey(ctx context.Context, keyID string) error {
	_, err := a.client.Delete(ctx, "/api/keys/"+keyID)
	return err
}

func (a *AuthClient) KeyAnalytics(ctx context.Context) ([]KeyAnalytics, error) {
	var resp []KeyAnalytics
	err := a.client.GetJSON(ctx, "/api/keys/analytics/by-key", nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}
