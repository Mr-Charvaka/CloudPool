package cloudpool

import (
	"math"
	"math/rand"
	"net/http"
	"time"
)

type doFunc func() (*http.Response, error)

func retry(maxAttempts int, fn doFunc) (*http.Response, error) {
	if maxAttempts <= 0 {
		maxAttempts = 1
	}
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
		wait := backoffDuration(attempt, 100*time.Millisecond, 120*time.Second)
		time.Sleep(wait)
	}
	return resp, err
}

func backoffDuration(attempt int, base, cap time.Duration) time.Duration {
	delay := float64(base) * math.Pow(2, float64(attempt))
	r := rand.New(rand.NewSource(time.Now().UnixNano()))
	jitter := r.Float64() * float64(base)
	delay = math.Min(delay+jitter, float64(cap))
	return time.Duration(delay)
}
