package cloudpool

import "fmt"

type APIError struct {
	StatusCode int
	Code       string `json:"code,omitempty"`
	Message    string `json:"error,omitempty"`
	Detail     string `json:"detail,omitempty"`
}

func (e *APIError) Error() string {
	s := fmt.Sprintf("[%d]", e.StatusCode)
	if e.Code != "" {
		s += " " + e.Code
	}
	if e.Message != "" {
		s += " " + e.Message
	}
	if e.Detail != "" {
		s += ": " + e.Detail
	}
	return s
}

func (e *APIError) Status() int {
	return e.StatusCode
}

type ValidationError struct {
	APIError
	Fields map[string]string `json:"fields,omitempty"`
}

type NotFoundError struct {
	APIError
	Resource string `json:"resource,omitempty"`
}

type AuthError struct {
	APIError
}

type RateLimitError struct {
	APIError
	RetryAfter int `json:"retryAfter,omitempty"`
}
