// Package backend is the HTTP client for the Content Guardian server. Only
// category labels ever leave the machine -- never URLs or page content.
package backend

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sync"
	"time"
)

type Client struct {
	baseURL string
	devID   string
	token   string
	http    *http.Client

	mu     sync.Mutex
	buffer []event
}

type event struct {
	Category   string    `json:"category"`
	OccurredAt time.Time `json:"occurredAt"`
}

func New(baseURL, deviceID, deviceToken string) *Client {
	return &Client{
		baseURL: baseURL,
		devID:   deviceID,
		token:   deviceToken,
		http:    &http.Client{Timeout: 10 * time.Second},
	}
}

func (c *Client) url(path string) string {
	return fmt.Sprintf("%s/api/v1/devices/%s%s", c.baseURL, c.devID, path)
}

func (c *Client) post(path string, body any) (*http.Response, error) {
	var r io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return nil, err
		}
		r = bytes.NewReader(b)
	}
	req, err := http.NewRequest(http.MethodPost, c.url(path), r)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Device-Token", c.token)
	return c.http.Do(req)
}

// Heartbeat signals the agent is alive. Missed heartbeats are what let the
// server raise a "device went dark" alert -- the real guarantee against a
// forced removal we cannot prevent.
func (c *Client) Heartbeat() error {
	resp, err := c.post("/heartbeat", nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return fmt.Errorf("heartbeat status %s", resp.Status)
	}
	return nil
}

// ReportCategory queues a verdict and flushes. Buffered on failure so verdicts
// observed while offline survive until connectivity returns.
func (c *Client) ReportCategory(category string) {
	c.mu.Lock()
	c.buffer = append(c.buffer, event{Category: category, OccurredAt: time.Now().UTC()})
	c.mu.Unlock()
	c.Flush()
}

func (c *Client) Flush() {
	c.mu.Lock()
	pending := c.buffer
	c.buffer = nil
	c.mu.Unlock()

	var keep []event
	for _, e := range pending {
		resp, err := c.post("/events", e)
		if err != nil || resp.StatusCode >= 300 {
			keep = append(keep, e)
			if resp != nil {
				resp.Body.Close()
			}
			continue
		}
		resp.Body.Close()
	}
	if len(keep) > 0 {
		c.mu.Lock()
		c.buffer = append(keep, c.buffer...)
		c.mu.Unlock()
	}
}

// RequestUninstall begins the server-authorized removal flow; the guardian is
// alerted and mailed a one-time code before this returns.
func (c *Client) RequestUninstall() (attemptID string, err error) {
	resp, err := c.post("/uninstall/request", nil)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return "", fmt.Errorf("request status %s", resp.Status)
	}
	var out struct {
		AttemptID string `json:"attemptId"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		return "", err
	}
	return out.AttemptID, nil
}

// VerifyUninstall exchanges the guardian's one-time code for the server's
// approval. A nil error means removal is authorized.
func (c *Client) VerifyUninstall(attemptID, code string) error {
	resp, err := c.post("/uninstall/verify", map[string]string{"attemptId": attemptID, "code": code})
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		b, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("not authorized: %s", string(b))
	}
	return nil
}
