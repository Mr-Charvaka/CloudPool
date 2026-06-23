package main

import (
	"encoding/json"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/gofiber/websocket/v2"
)

type Tunnel struct {
	Subdomain string `json:"subdomain"`
	BytesSent uint64 `json:"bytes_sent"`
	BytesRecv uint64 `json:"bytes_recv"`
}

type CapturedRequest struct {
	ID        string     `json:"id"`
	TunnelID  string     `json:"tunnel_id"`
	Timestamp time.Time  `json:"timestamp"`
	Method    string     `json:"method"`
	Path      string     `json:"path"`
	Host      string     `json:"host"`
	Headers   [][]string `json:"headers"`
}

type Metrics struct {
	TotalTunnels   int               `json:"total_tunnels"`
	Tunnels       []Tunnel          `json:"tunnels"`
	RequestHistory []CapturedRequest `json:"request_history"`
}

var (
	currentMetrics Metrics
	metricsMutex   sync.RWMutex
	clients        = make(map[*websocket.Conn]bool)
	clientsMutex   sync.Mutex
)

func main() {
	app := fiber.New()

	app.Use(logger.New())
	app.Use(cors.New())

	// WebSocket for real-time updates
	app.Get("/ws", websocket.New(func(c *websocket.Conn) {
		clientsMutex.Lock()
		clients[c] = true
		clientsMutex.Unlock()

		defer func() {
			clientsMutex.Lock()
			delete(clients, c)
			clientsMutex.Unlock()
			c.Close()
		}()

		for {
			if _, _, err := c.ReadMessage(); err != nil {
				break
			}
		}
	}))

	app.Get("/api/status", func(c *fiber.Ctx) error {
		metricsMutex.RLock()
		defer metricsMutex.RUnlock()
		return c.JSON(currentMetrics)
	})

	app.Post("/api/replay/:id", func(c *fiber.Ctx) error {
		id := c.Params("id")
		resp, err := http.Post("http://127.0.0.1:9090/internal/replay/"+id, "application/json", nil)
		if err != nil {
			return c.Status(500).SendString(err.Error())
		}
		defer resp.Body.Close()
		return c.SendString("OK")
	})

	// Background worker to poll the Rust Relay Server
	go pollRustMetrics()

	// Broadcaster
	go broadcastMetrics()

	log.Fatal(app.Listen(":4000"))
}

func pollRustMetrics() {
	for {
		resp, err := http.Get("http://127.0.0.1:9090/internal/metrics")
		if err != nil {
			log.Printf("Error polling Rust metrics: %v", err)
			time.Sleep(2 * time.Second)
			continue
		}

		var metrics Metrics
		if err := json.NewDecoder(resp.Body).Decode(&metrics); err != nil {
			log.Printf("Error decoding metrics: %v", err)
		} else {
			metricsMutex.Lock()
			currentMetrics = metrics
			metricsMutex.Unlock()
		}
		resp.Body.Close()

		time.Sleep(1 * time.Second)
	}
}

func broadcastMetrics() {
	for {
		time.Sleep(1 * time.Second)

		metricsMutex.RLock()
		data, _ := json.Marshal(currentMetrics)
		metricsMutex.RUnlock()

		clientsMutex.Lock()
		for client := range clients {
			if err := client.WriteMessage(websocket.TextMessage, data); err != nil {
				client.Close()
				delete(clients, client)
			}
		}
		clientsMutex.Unlock()
	}
}
