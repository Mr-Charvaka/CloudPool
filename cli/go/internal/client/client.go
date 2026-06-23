package client

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path"
	"strings"
	"time"

	"github.com/cloudpool/cli/internal/credstore"
	"github.com/schollz/progressbar/v3"
)

type CloudPoolClient struct {
	baseURL    string
	client     *http.Client
	jwtToken   string
	apiKey     string
	projectID  string
	retryMax   int
	verbose    bool
	insecure   bool
	noProgress bool
	credStore  credstore.Store
	middleware []Middleware
	userAgent  string
}

type Option func(*CloudPoolClient)

func WithJWT(token string) Option {
	return func(c *CloudPoolClient) { c.jwtToken = token }
}

func WithAPIKey(key string) Option {
	return func(c *CloudPoolClient) { c.apiKey = key }
}

func WithProjectID(id string) Option {
	return func(c *CloudPoolClient) { c.projectID = id }
}

func WithRetryMax(n int) Option {
	return func(c *CloudPoolClient) { c.retryMax = n }
}

func WithVerbose(v bool) Option {
	return func(c *CloudPoolClient) { c.verbose = v }
}

func WithInsecure(v bool) Option {
	return func(c *CloudPoolClient) { c.insecure = v }
}

func WithNoProgress(v bool) Option {
	return func(c *CloudPoolClient) { c.noProgress = v }
}

func WithCredStore(s credstore.Store) Option {
	return func(c *CloudPoolClient) { c.credStore = s }
}

func WithUserAgent(ua string) Option {
	return func(c *CloudPoolClient) { c.userAgent = ua }
}

func WithTimeout(d time.Duration) Option {
	return func(c *CloudPoolClient) {
		if tr, ok := c.client.Transport.(*http.Transport); ok {
			tr.ResponseHeaderTimeout = d
		}
	}
}

func New(baseURL string, opts ...Option) *CloudPoolClient {
	tr := &http.Transport{
		MaxIdleConns:        25,
		MaxIdleConnsPerHost: 10,
		IdleConnTimeout:     90 * time.Second,
		DisableCompression:  false,
	}
	c := &CloudPoolClient{
		baseURL:   strings.TrimRight(baseURL, "/"),
		retryMax:  3,
		userAgent: "CloudPoolCLI/0.1.0",
		client: &http.Client{
			Timeout:   0,
			Transport: tr,
		},
	}
	for _, o := range opts {
		o(c)
	}
	if c.insecure {
		tr.TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
	}
	return c
}

type ErrorResponse struct {
	Code    int    `json:"-"`
	Message string `json:"error,omitempty"`
	Detail  string `json:"detail,omitempty"`
}

func (e *ErrorResponse) Error() string {
	s := fmt.Sprintf("[%d]", e.Code)
	if e.Message != "" {
		s += " " + e.Message
	}
	if e.Detail != "" {
		s += ": " + e.Detail
	}
	return s
}

type Request struct {
	Method  string
	Path    string
	Body    interface{}
	Params  map[string]string
	Headers http.Header
	Context context.Context
}

func (c *CloudPoolClient) do(req *Request) (*http.Response, error) {
	u, err := url.Parse(c.baseURL + req.Path)
	if err != nil {
		return nil, fmt.Errorf("invalid url %s: %w", req.Path, err)
	}
	q := u.Query()
	for k, v := range req.Params {
		q.Set(k, v)
	}
	u.RawQuery = q.Encode()

	var bodyReader io.Reader
	if req.Body != nil {
		switch b := req.Body.(type) {
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

	httpReq, err := http.NewRequestWithContext(req.Context, req.Method, u.String(), bodyReader)
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}

	httpReq.Header = c.buildHeaders()
	if req.Headers != nil {
		for k, vs := range req.Headers {
			for _, v := range vs {
				httpReq.Header.Add(k, v)
			}
		}
	}
	if req.Body != nil {
		httpReq.Header.Set("Content-Type", "application/json")
	}

	if c.verbose {
		fmt.Fprintf(os.Stderr, "[verbose] %s %s\n", req.Method, u.String())
	}

	resp, err := c.client.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	return resp, nil
}

func (c *CloudPoolClient) buildHeaders() http.Header {
	h := http.Header{}
	h.Set("User-Agent", c.userAgent)
	h.Set("Accept", "application/json")
	if c.apiKey != "" {
		h.Set("X-API-KEY", c.apiKey)
	} else if c.jwtToken != "" {
		h.Set("Authorization", "Bearer "+c.jwtToken)
	}
	if c.projectID != "" {
		h.Set("X-Project-Id", c.projectID)
	}
	return h
}

func (c *CloudPoolClient) readBody(resp *http.Response) ([]byte, error) {
	defer resp.Body.Close()
	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read body: %w", err)
	}
	if resp.StatusCode >= 400 {
		errResp := &ErrorResponse{Code: resp.StatusCode}
		if err := json.Unmarshal(data, errResp); err == nil && errResp.Message != "" {
			return nil, errResp
		}
		return nil, fmt.Errorf("[%d] %s", resp.StatusCode, SanitizeErrorBody(string(data)))
	}
	return data, nil
}

// SanitizeErrorBody cleans potentially dangerous or verbose error payloads
// (e.g. HTML proxy error pages) before displaying them to users.
func SanitizeErrorBody(body string) string {
	trimmed := strings.TrimSpace(body)
	if len(trimmed) == 0 {
		return "empty response body"
	}
	// Detect HTML proxy error pages (NGINX, Cloudflare, etc.)
	lower := strings.ToLower(trimmed)
	if strings.HasPrefix(lower, "<html") || strings.HasPrefix(lower, "<!doctype") ||
		strings.HasPrefix(lower, "<!DOCTYPE") {
		return "non-JSON error (HTML proxy response)"
	}
	// Truncate to a reasonable length to avoid terminal pollution.
	if len(trimmed) > 500 {
		trimmed = trimmed[:500] + "..."
	}
	return trimmed
}

func (c *CloudPoolClient) Get(ctx context.Context, path string, params map[string]string) ([]byte, error) {
	return c.call(ctx, &Request{Method: "GET", Path: path, Params: params, Context: ctx})
}

func (c *CloudPoolClient) Post(ctx context.Context, path string, body interface{}, params map[string]string) ([]byte, error) {
	return c.call(ctx, &Request{Method: "POST", Path: path, Body: body, Params: params, Context: ctx})
}

func (c *CloudPoolClient) Put(ctx context.Context, path string, body interface{}) ([]byte, error) {
	return c.call(ctx, &Request{Method: "PUT", Path: path, Body: body, Context: ctx})
}

func (c *CloudPoolClient) Delete(ctx context.Context, path string) ([]byte, error) {
	return c.call(ctx, &Request{Method: "DELETE", Path: path, Context: ctx})
}

// GetJSON issues a GET and unmarshals the JSON response into target.
func (c *CloudPoolClient) GetJSON(ctx context.Context, path string, params map[string]string, target interface{}) error {
	data, err := c.Get(ctx, path, params)
	if err != nil {
		return err
	}
	return c.unmarshalResponse(data, target)
}

// PostJSON issues a POST and unmarshals the JSON response into target.
func (c *CloudPoolClient) PostJSON(ctx context.Context, path string, body interface{}, params map[string]string, target interface{}) error {
	data, err := c.Post(ctx, path, body, params)
	if err != nil {
		return err
	}
	return c.unmarshalResponse(data, target)
}

// PutJSON issues a PUT and unmarshals the JSON response into target.
func (c *CloudPoolClient) PutJSON(ctx context.Context, path string, body interface{}, target interface{}) error {
	data, err := c.Put(ctx, path, body)
	if err != nil {
		return err
	}
	return c.unmarshalResponse(data, target)
}

// DeleteJSON issues a DELETE and unmarshals the JSON response into target.
func (c *CloudPoolClient) DeleteJSON(ctx context.Context, path string, target interface{}) error {
	data, err := c.Delete(ctx, path)
	if err != nil {
		return err
	}
	return c.unmarshalResponse(data, target)
}

func (c *CloudPoolClient) unmarshalResponse(data []byte, target interface{}) error {
	if len(data) == 0 {
		return nil
	}
	if err := json.Unmarshal(data, target); err != nil {
		return fmt.Errorf("parse response: %w", err)
	}
	return nil
}

// DoRaw performs a request without reading or closing the body.
// The caller MUST close resp.Body when done.
func (c *CloudPoolClient) DoRaw(ctx context.Context, method, path string, params map[string]string) (*http.Response, error) {
	req := &Request{Method: method, Path: path, Params: params, Context: ctx}
	for _, mw := range c.middleware {
		mw(req)
	}
	return c.do(req)
}

func (c *CloudPoolClient) call(ctx context.Context, req *Request) ([]byte, error) {
	if req.Context == nil {
		req.Context = ctx
	}
	for _, mw := range c.middleware {
		mw(req)
	}
	doFn := func() (*http.Response, error) { return c.do(req) }

	var resp *http.Response
	var err error
	if c.retryMax > 1 {
		resp, err = retry(c.retryMax, doFn)
	} else {
		resp, err = doFn()
	}
	if err != nil {
		return nil, err
	}
	return c.readBody(resp)
}

func (c *CloudPoolClient) Upload(ctx context.Context, path, filePath, bucket string) ([]byte, error) {
	u, err := url.Parse(c.baseURL + path)
	if err != nil {
		return nil, fmt.Errorf("invalid url: %w", err)
	}
	q := u.Query()
	q.Set("bucket", bucket)
	u.RawQuery = q.Encode()

	file, err := os.Open(filePath)
	if err != nil {
		return nil, fmt.Errorf("open file %s: %w", filePath, err)
	}
	defer file.Close()

	stat, err := file.Stat()
	if err != nil {
		return nil, fmt.Errorf("stat file: %w", err)
	}

	// Use io.Pipe to stream multipart data directly into the HTTP request
	// without buffering the entire file in memory.
	pipeR, pipeW := io.Pipe()
	mw := newMultipartWriter(pipeW)

	type pipeResult struct {
		err error
	}
	done := make(chan pipeResult, 1)

	go func() {
		defer pipeW.Close()
		defer mw.writer.Close()

		part, err := mw.createFormFile("file", path.Base(filePath))
		if err != nil {
			done <- pipeResult{err: fmt.Errorf("create form file: %w", err)}
			return
		}

		var writer io.Writer = part
		var bar *progressbar.ProgressBar
		if !c.noProgress && stat.Size() > 1024*1024 {
			bar = progressbar.DefaultBytes(stat.Size(), "uploading")
			writer = io.MultiWriter(part, bar)
		}

		if _, err := io.Copy(writer, file); err != nil {
			done <- pipeResult{err: fmt.Errorf("copy file: %w", err)}
			return
		}
		if bar != nil {
			bar.Finish()
		}
		done <- pipeResult{}
	}()

	contentType := mw.contentType()

	httpReq, err := http.NewRequestWithContext(ctx, "POST", u.String(), pipeR)
	if err != nil {
		return nil, fmt.Errorf("create upload request: %w", err)
	}
	httpReq.Header = c.buildHeaders()
	httpReq.Header.Set("Content-Type", contentType)

	resp, err := c.client.Do(httpReq)
	if err != nil {
		pipeR.Close()
		return nil, fmt.Errorf("upload failed: %w", err)
	}

	// Wait for the pipe goroutine to finish and check for errors.
	result := <-done
	if result.err != nil {
		resp.Body.Close()
		return nil, result.err
	}

	return c.readBody(resp)
}

func (c *CloudPoolClient) Download(ctx context.Context, path string, writer io.Writer) (string, error) {
	resp, err := c.do(&Request{Method: "GET", Path: path, Context: ctx})
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(resp.Body)
		return "", fmt.Errorf("[%d] %s", resp.StatusCode, SanitizeErrorBody(string(body)))
	}

	if _, err := io.Copy(writer, resp.Body); err != nil {
		return "", fmt.Errorf("download write: %w", err)
	}

	filename := "download"
	if cd := resp.Header.Get("Content-Disposition"); cd != "" {
		if parts := strings.Split(cd, "filename="); len(parts) > 1 {
			filename = strings.Trim(parts[1], `" `)
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
