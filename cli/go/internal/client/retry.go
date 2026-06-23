package client

import (
	"math"
	"math/rand"
	"net/http"
	"time"
)

func init() {
	rand.Seed(time.Now().UnixNano())
}

type doFunc func() (*http.Response, error)

func retry(maxAttempts int, fn doFunc) (*http.Response, error) {
	var resp *http.Response
	var err error

	for attempt := 0; attempt < maxAttempts; attempt++ {
		resp, err = fn()
		if err == nil && resp.StatusCode < 500 && resp.StatusCode != 429 {
			return resp, nil
		}
		if resp != nil {
			resp.Body.Close()
		}
		if attempt == maxAttempts-1 {
			break
		}
		wait := backoff(attempt, 100*time.Millisecond, 5*time.Second)
		time.Sleep(wait)
	}
	return resp, err
}

func backoff(attempt int, min, max time.Duration) time.Duration {
	delay := float64(min) * math.Pow(2, float64(attempt))
	jitter := rand.Float64() * float64(min)
	delay = math.Min(delay+jitter, float64(max))
	return time.Duration(delay)
}
