package cloudpool

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/textproto"
	"net/url"
	"os"
	"path"
	"strings"
	"time"
)

type ClientConfig struct {
	BaseURL  string
	APIKey   string
	JWTToken string
}

type Option func(*CloudPoolClient)

func WithTimeout(d time.Duration) Option {
	return func(c *CloudPoolClient) { c.httpClient.Timeout = d }
}

func WithRetryMax(n int) Option {
	return func(c *CloudPoolClient) { c.retryMax = n }
}

func WithInsecure(v bool) Option {
	return func(c *CloudPoolClient) { c.insecure = v }
}

func WithUserAgent(ua string) Option {
	return func(c *CloudPoolClient) { c.userAgent = ua }
}

func WithHTTPClient(client *http.Client) Option {
	return func(c *CloudPoolClient) { c.httpClient = client }
}

type CloudPoolClient struct {
	baseURL    string
	apiKey     string
	jwtToken   string
	httpClient *http.Client
	retryMax   int
	insecure   bool
	userAgent  string
	middleware []Middleware

	Auth     *AuthClient
	Database *DatabaseClient
	Files    *FilesClient
	Vector   *VectorClient
	Compute  *ComputeClient
	Network  *NetworkClient
	Payments *PaymentsClient
	KV       *KVClient
	Emails   *EmailsClient
}

func NewClient(config ClientConfig) *CloudPoolClient {
	baseURL := strings.TrimSuffix(config.BaseURL, "/")
	if baseURL == "" {
		baseURL = "http://localhost:8080/api"
	}
	tr := &http.Transport{
		MaxIdleConns:        25,
		MaxIdleConnsPerHost: 10,
		IdleConnTimeout:     90 * time.Second,
		DisableCompression:  false,
	}
	c := &CloudPoolClient{
		baseURL:  baseURL,
		apiKey:   config.APIKey,
		jwtToken: config.JWTToken,
		httpClient: &http.Client{
			Timeout:   30 * time.Second,
			Transport: tr,
		},
		retryMax:  3,
		userAgent: "CloudPoolSDK/0.1.0",
	}
	c.Auth = &AuthClient{client: c}
	c.Database = &DatabaseClient{client: c}
	c.Files = &FilesClient{client: c}
	c.Vector = &VectorClient{client: c}
	c.Compute = &ComputeClient{client: c}
	c.Network = &NetworkClient{client: c}
	c.Payments = &PaymentsClient{client: c}
	c.KV = &KVClient{client: c}
	c.Emails = &EmailsClient{client: c}
	return c
}

func (c *CloudPoolClient) Use(opts ...Option) {
	for _, o := range opts {
		o(c)
	}
	if c.insecure {
		if tr, ok := c.httpClient.Transport.(*http.Transport); ok {
			tr.TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
		}
	}
}

func (c *CloudPoolClient) UseMiddleware(mw ...Middleware) {
	c.middleware = append(c.middleware, mw...)
}

func (c *CloudPoolClient) Request(method, path string, body interface{}, headers map[string]string) ([]byte, error) {
	return c.request(context.Background(), method, path, body, headers)
}

func (c *CloudPoolClient) request(ctx context.Context, method, reqPath string, body interface{}, headers map[string]string) ([]byte, error) {
	u, err := url.Parse(c.baseURL + "/" + strings.TrimPrefix(reqPath, "/"))
	if err != nil {
		return nil, fmt.Errorf("invalid url: %w", err)
	}

	var bodyReader io.Reader
	if body != nil {
		jsonBytes, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("failed to marshal request body: %w", err)
		}
		bodyReader = bytes.NewReader(jsonBytes)
	}

	req, err := http.NewRequestWithContext(ctx, method, u.String(), bodyReader)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	if body != nil && bodyReader != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	c.setHeaders(req)

	for k, v := range headers {
		req.Header.Set(k, v)
	}

	for _, mw := range c.middleware {
		mw(req)
	}

	doFn := func() (*http.Response, error) { return c.httpClient.Do(req) }

	resp, err := retry(c.retryMax, doFn)
	if err != nil {
		return nil, fmt.Errorf("http request failed: %w", err)
	}
	defer resp.Body.Close()

	respBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		apiErr := &APIError{StatusCode: resp.StatusCode}
		if err := json.Unmarshal(respBytes, apiErr); err == nil && apiErr.Message != "" {
			return nil, apiErr
		}
		return nil, &APIError{
			StatusCode: resp.StatusCode,
			Message:    sanitizeErrorBody(string(respBytes)),
		}
	}

	return respBytes, nil
}

func (c *CloudPoolClient) setHeaders(req *http.Request) {
	req.Header.Set("User-Agent", c.userAgent)
	req.Header.Set("Accept", "application/json")
	if c.apiKey != "" {
		req.Header.Set("X-API-KEY", c.apiKey)
	} else if c.jwtToken != "" {
		req.Header.Set("Authorization", "Bearer "+c.jwtToken)
	}
}

func (c *CloudPoolClient) Get(ctx context.Context, path string, params map[string]string) ([]byte, error) {
	return c.call(ctx, &requestParams{Method: "GET", Path: path, Params: params})
}

func (c *CloudPoolClient) Post(ctx context.Context, path string, body interface{}, params map[string]string) ([]byte, error) {
	return c.call(ctx, &requestParams{Method: "POST", Path: path, Body: body, Params: params})
}

func (c *CloudPoolClient) Put(ctx context.Context, path string, body interface{}) ([]byte, error) {
	return c.call(ctx, &requestParams{Method: "PUT", Path: path, Body: body})
}

func (c *CloudPoolClient) Delete(ctx context.Context, path string) ([]byte, error) {
	return c.call(ctx, &requestParams{Method: "DELETE", Path: path})
}

type requestParams struct {
	Method  string
	Path    string
	Body    interface{}
	Params  map[string]string
	Headers map[string]string
}

func (c *CloudPoolClient) call(ctx context.Context, rp *requestParams) ([]byte, error) {
	u, err := url.Parse(c.baseURL + "/" + strings.TrimPrefix(rp.Path, "/"))
	if err != nil {
		return nil, fmt.Errorf("invalid url: %w", err)
	}
	q := u.Query()
	for k, v := range rp.Params {
		q.Set(k, v)
	}
	u.RawQuery = q.Encode()

	var bodyReader io.Reader
	if rp.Body != nil {
		switch b := rp.Body.(type) {
		case *bytes.Buffer:
			bodyReader = b
		case io.Reader:
			bodyReader = b
		default:
			jsonBytes, err := json.Marshal(b)
			if err != nil {
				return nil, fmt.Errorf("marshal body: %w", err)
			}
			bodyReader = bytes.NewReader(jsonBytes)
		}
	}

	req, err := http.NewRequestWithContext(ctx, rp.Method, u.String(), bodyReader)
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}

	c.setHeaders(req)
	if rp.Headers != nil {
		for k, v := range rp.Headers {
			req.Header.Set(k, v)
		}
	}
	if rp.Body != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	for _, mw := range c.middleware {
		mw(req)
	}

	doFn := func() (*http.Response, error) { return c.httpClient.Do(req) }

	resp, err := retry(c.retryMax, doFn)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read body: %w", err)
	}

	if resp.StatusCode >= 400 {
		apiErr := &APIError{StatusCode: resp.StatusCode}
		if err := json.Unmarshal(data, apiErr); err == nil && apiErr.Message != "" {
			return nil, apiErr
		}
		return nil, &APIError{
			StatusCode: resp.StatusCode,
			Message:    sanitizeErrorBody(string(data)),
		}
	}
	return data, nil
}

func (c *CloudPoolClient) GetJSON(ctx context.Context, path string, params map[string]string, target interface{}) error {
	data, err := c.Get(ctx, path, params)
	if err != nil {
		return err
	}
	return unmarshalResponse(data, target)
}

func (c *CloudPoolClient) PostJSON(ctx context.Context, path string, body interface{}, params map[string]string, target interface{}) error {
	data, err := c.Post(ctx, path, body, params)
	if err != nil {
		return err
	}
	return unmarshalResponse(data, target)
}

func (c *CloudPoolClient) PutJSON(ctx context.Context, path string, body interface{}, target interface{}) error {
	data, err := c.Put(ctx, path, body)
	if err != nil {
		return err
	}
	return unmarshalResponse(data, target)
}

func (c *CloudPoolClient) DeleteJSON(ctx context.Context, path string, target interface{}) error {
	data, err := c.Delete(ctx, path)
	if err != nil {
		return err
	}
	return unmarshalResponse(data, target)
}

func unmarshalResponse(data []byte, target interface{}) error {
	if len(data) == 0 {
		return nil
	}
	if err := json.Unmarshal(data, target); err != nil {
		return fmt.Errorf("parse response: %w", err)
	}
	return nil
}

func (c *CloudPoolClient) Upload(ctx context.Context, path, filePath, bucket string) ([]byte, error) {
	u, err := url.Parse(c.baseURL + "/" + strings.TrimPrefix(path, "/"))
	if err != nil {
		return nil, fmt.Errorf("invalid url: %w", err)
	}
	q := u.Query()
	if bucket != "" {
		q.Set("bucket", bucket)
	}
	u.RawQuery = q.Encode()

	file, err := os.Open(filePath)
	if err != nil {
		return nil, fmt.Errorf("open file: %w", err)
	}
	defer file.Close()

	pipeR, pipeW := io.Pipe()
	mw := multipart.NewWriter(pipeW)

	type pipeResult struct{ err error }
	done := make(chan pipeResult, 1)

	go func() {
		defer pipeW.Close()
		defer mw.Close()

		h := make(textproto.MIMEHeader)
		h.Set("Content-Disposition",
			fmt.Sprintf(`form-data; name="file"; filename="%s"`, path.Base(filePath)))
		h.Set("Content-Type", "application/octet-stream")

		part, err := mw.CreatePart(h)
		if err != nil {
			done <- pipeResult{err: fmt.Errorf("create form part: %w", err)}
			return
		}
		if _, err := io.Copy(part, file); err != nil {
			done <- pipeResult{err: fmt.Errorf("copy file: %w", err)}
			return
		}
		done <- pipeResult{}
	}()

	contentType := mw.FormDataContentType()

	req, err := http.NewRequestWithContext(ctx, "POST", u.String(), pipeR)
	if err != nil {
		pipeR.Close()
		return nil, fmt.Errorf("create upload request: %w", err)
	}
	c.setHeaders(req)
	req.Header.Set("Content-Type", contentType)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		pipeR.Close()
		return nil, fmt.Errorf("upload failed: %w", err)
	}
	defer resp.Body.Close()

	result := <-done
	if result.err != nil {
		return nil, result.err
	}

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode >= 400 {
		apiErr := &APIError{StatusCode: resp.StatusCode}
		if err := json.Unmarshal(data, apiErr); err == nil && apiErr.Message != "" {
			return nil, apiErr
		}
		return nil, &APIError{
			StatusCode: resp.StatusCode,
			Message:    sanitizeErrorBody(string(data)),
		}
	}
	return data, nil
}

func (c *CloudPoolClient) Download(ctx context.Context, path string, writer io.Writer) (string, error) {
	u := c.baseURL + "/" + strings.TrimPrefix(path, "/")
	req, err := http.NewRequestWithContext(ctx, "GET", u, nil)
	if err != nil {
		return "", fmt.Errorf("create download request: %w", err)
	}
	c.setHeaders(req)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("download failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(resp.Body)
		return "", &APIError{
			StatusCode: resp.StatusCode,
			Message:    sanitizeErrorBody(string(body)),
		}
	}

	if _, err := io.Copy(writer, resp.Body); err != nil {
		return "", fmt.Errorf("download write: %w", err)
	}

	filename := "download"
	if cd := resp.Header.Get("Content-Disposition"); cd != "" {
		for _, part := range strings.Split(cd, "filename=") {
			if trimmed := strings.Trim(part, `" `); trimmed != cd {
				filename = trimmed
				break
			}
		}
	}
	return filename, nil
}

func (c *CloudPoolClient) GraphQL(ctx context.Context, query string, variables map[string]interface{}) ([]byte, error) {
	body := map[string]interface{}{"query": query}
	if variables != nil {
		body["variables"] = variables
	}
	return c.Post(ctx, "/graphql", body, nil)
}

func (c *CloudPoolClient) GraphQLJSON(ctx context.Context, query string, variables map[string]interface{}, target interface{}) error {
	data, err := c.GraphQL(ctx, query, variables)
	if err != nil {
		return err
	}
	return unmarshalResponse(data, target)
}

func sanitizeErrorBody(body string) string {
	trimmed := strings.TrimSpace(body)
	if len(trimmed) == 0 {
		return "empty response body"
	}
	lower := strings.ToLower(trimmed)
	if strings.HasPrefix(lower, "<html") || strings.HasPrefix(lower, "<!doctype") ||
		strings.HasPrefix(lower, "<!DOCTYPE") {
		return "non-JSON error (HTML proxy response)"
	}
	if len(trimmed) > 500 {
		trimmed = trimmed[:500] + "..."
	}
	return trimmed
}

type Middleware func(*http.Request)

func LoggingMiddleware() Middleware {
	return func(req *http.Request) {
		fmt.Fprintf(os.Stderr, "[sdk] %s %s\n", req.Method, req.URL.String())
	}
}

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
	claims, err := decodeJWT(tm.client.jwtToken)
	if err != nil {
		return tm.refresh(ctx)
	}
	exp, ok := claims["exp"].(float64)
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
		"token": tm.client.jwtToken,
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
	parts := strings.SplitN(token, ".", 3)
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

func base64Decode(s string) ([]byte, error) {
	return base64.RawURLEncoding.DecodeString(s)
}
