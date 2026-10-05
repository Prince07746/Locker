// Package main is the cross-platform portion of the agent: the parts that
// reduce cleanly to code and are shared across Windows and macOS -- the
// reporter, the heartbeat, and the uninstall-authorization client.
//
// The platform-specific pieces that CANNOT live here (and are a separate
// workstream) are the actual filter enforcement (Windows WFP / macOS
// NetworkExtension) and the on-device ML classifier. This client is what
// those modules call to report verdicts and to gate removal.
package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"sync"
	"time"
)

type Config struct {
	BaseURL     string // e.g. https://api.yourdomain.com
	DeviceID    string
	DeviceToken string
}

func configFromEnv() Config {
	return Config{
		BaseURL:     os.Getenv("GUARDIAN_BASE_URL"),
		DeviceID:    os.Getenv("GUARDIAN_DEVICE_ID"),
		DeviceToken: os.Getenv("GUARDIAN_DEVICE_TOKEN"),
	}
}

type Client struct {
	cfg  Config
	http *http.Client

	mu     sync.Mutex
	buffer []event // local buffer so verdicts observed while offline are not lost
}

type event struct {
	Category   string    `json:"category"`
	OccurredAt time.Time `json:"occurredAt"`
}

func NewClient(cfg Config) *Client {
	return &Client{cfg: cfg, http: &http.Client{Timeout: 10 * time.Second}}
}

func (c *Client) url(path string) string {
	return fmt.Sprintf("%s/api/v1/devices/%s%s", c.cfg.BaseURL, c.cfg.DeviceID, path)
}

func (c *Client) post(path string, body any) (*http.Response, error) {
	var buf io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return nil, err
		}
		buf = bytes.NewReader(b)
	}
	req, err := http.NewRequest(http.MethodPost, c.url(path), buf)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Device-Token", c.cfg.DeviceToken)
	return c.http.Do(req)
}

// Heartbeat tells the backend the agent is alive. Missing heartbeats are what
// let the backend fire a "device went dark" alert -- the real guarantee when a
// forced uninstall cannot be prevented.
func (c *Client) Heartbeat() error {
	resp, err := c.post("/heartbeat", nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return fmt.Errorf("heartbeat failed: %s", resp.Status)
	}
	return nil
}

// ReportCategory is called by the on-device classifier when it blocks content.
// Only the category label crosses the wire -- never the URL or page content.
func (c *Client) ReportCategory(category string) {
	c.mu.Lock()
	c.buffer = append(c.buffer, event{Category: category, OccurredAt: time.Now().UTC()})
	c.mu.Unlock()
	c.flush()
}

func (c *Client) flush() {
	c.mu.Lock()
	pending := c.buffer
	c.buffer = nil
	c.mu.Unlock()

	var stillPending []event
	for _, e := range pending {
		resp, err := c.post("/events", e)
		if err != nil || resp.StatusCode >= 300 {
			stillPending = append(stillPending, e) // keep for the next attempt
			if resp != nil {
				resp.Body.Close()
			}
			continue
		}
		resp.Body.Close()
	}
	if len(stillPending) > 0 {
		c.mu.Lock()
		c.buffer = append(stillPending, c.buffer...)
		c.mu.Unlock()
	}
}

// RequestUninstall begins the server-authorized removal flow. The guardian is
// alerted and mailed a one-time code before this returns.
func (c *Client) RequestUninstall() (attemptID string, err error) {
	resp, err := c.post("/uninstall/request", nil)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return "", fmt.Errorf("request failed: %s", resp.Status)
	}
	var out struct {
		AttemptID string `json:"attemptId"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		return "", err
	}
	return out.AttemptID, nil
}

// VerifyUninstall exchanges the guardian's one-time code for a short-lived,
// signed uninstall token. The uninstaller must validate this token's signature
// before proceeding. Returns an error if the code is wrong or expired.
func (c *Client) VerifyUninstall(attemptID, code string) (uninstallToken string, err error) {
	resp, err := c.post("/uninstall/verify", map[string]string{"attemptId": attemptID, "code": code})
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return "", fmt.Errorf("verify failed: %s", resp.Status)
	}
	var out struct {
		UninstallToken string `json:"uninstallToken"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		return "", err
	}
	return out.UninstallToken, nil
}

func main() {
	cfg := configFromEnv()
	if cfg.BaseURL == "" || cfg.DeviceID == "" || cfg.DeviceToken == "" {
		log.Fatal("set GUARDIAN_BASE_URL, GUARDIAN_DEVICE_ID, GUARDIAN_DEVICE_TOKEN")
	}
	client := NewClient(cfg)

	// Heartbeat every 60s. Keep this comfortably under the server's timeout.
	ticker := time.NewTicker(60 * time.Second)
	defer ticker.Stop()

	log.Printf("agent started for device %s", cfg.DeviceID)
	if err := client.Heartbeat(); err != nil {
		log.Printf("initial heartbeat: %v", err)
	}
	for range ticker.C {
		if err := client.Heartbeat(); err != nil {
			log.Printf("heartbeat: %v", err)
		}
		client.flush() // retry any buffered verdicts
	}
}
