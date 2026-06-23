package client

import (
	"log"
	"time"
)

type Middleware func(*Request)

func LoggingMiddleware(verbose bool) Middleware {
	if !verbose {
		return func(r *Request) {}
	}
	return func(r *Request) {
		log.Printf("[req] %s %s", r.Method, r.Path)
		start := time.Now()
		defer func() {
			log.Printf("[req] %s %s completed in %v", r.Method, r.Path, time.Since(start))
		}()
	}
}
