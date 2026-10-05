package config

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
)

// Config holds everything the agent needs. It is written at install time to
// %ProgramData%\Locker\config.json and is readable only by administrators and
// the SYSTEM account the service runs as.
type Config struct {
	ServerBaseURL    string `json:"serverBaseUrl"`    // e.g. https://myguardian.duckdns.org
	DeviceID         string `json:"deviceId"`         // from POST /api/v1/guardian/devices
	DeviceToken      string `json:"deviceToken"`      // returned once at registration
	UpstreamDNS      string `json:"upstreamDns"`      // where allowed queries are forwarded
	ListenAddr       string `json:"listenAddr"`       // local resolver bind address
	BlocklistPath    string `json:"blocklistPath"`    // category blocklist file
	HeartbeatSeconds int    `json:"heartbeatSeconds"` // keep under the server timeout
}

func Dir() string {
	base := os.Getenv("ProgramData")
	if base == "" {
		base = `C:\ProgramData`
	}
	return filepath.Join(base, "Locker")
}

func Path() string { return filepath.Join(Dir(), "config.json") }

func defaults(c *Config) {
	if c.UpstreamDNS == "" {
		c.UpstreamDNS = "9.9.9.9:53" // Quad9; swap for your preferred resolver
	}
	if c.ListenAddr == "" {
		c.ListenAddr = "127.0.0.1:53"
	}
	if c.BlocklistPath == "" {
		c.BlocklistPath = filepath.Join(Dir(), "blocklist.txt")
	}
	if c.HeartbeatSeconds == 0 {
		c.HeartbeatSeconds = 60
	}
}

func Load() (*Config, error) {
	b, err := os.ReadFile(Path())
	if err != nil {
		return nil, fmt.Errorf("read config: %w", err)
	}
	var c Config
	if err := json.Unmarshal(b, &c); err != nil {
		return nil, fmt.Errorf("parse config: %w", err)
	}
	defaults(&c)
	if c.ServerBaseURL == "" || c.DeviceID == "" || c.DeviceToken == "" {
		return nil, fmt.Errorf("config missing serverBaseUrl/deviceId/deviceToken")
	}
	return &c, nil
}

func Save(c *Config) error {
	if err := os.MkdirAll(Dir(), 0o755); err != nil {
		return err
	}
	b, _ := json.MarshalIndent(c, "", "  ")
	return os.WriteFile(Path(), b, 0o600)
}
