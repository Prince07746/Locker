package service

import (
	"fmt"
	"log"
	"time"

	"golang.org/x/sys/windows/svc"
	"golang.org/x/sys/windows/svc/mgr"

	"github.com/Prince07746/Locker/agent/internal/backend"
	"github.com/Prince07746/Locker/agent/internal/config"
	"github.com/Prince07746/Locker/agent/internal/filter"
	"github.com/Prince07746/Locker/agent/internal/sysdns"
)

const (
	Name        = "LockerAgent"
	DisplayName = "Locker Content Protection"
	Description = "Filters content and reports accountability events. Removal is guardian-authorized."
)

// core wires together the blocklist, DNS proxy, DNS redirection, and heartbeat.
type core struct {
	cfg   *config.Config
	proxy *filter.Proxy
	stop  chan struct{}
}

func startCore() (*core, error) {
	cfg, err := config.Load()
	if err != nil {
		return nil, err
	}
	list := filter.NewBlocklist()
	if err := list.Load(cfg.BlocklistPath); err != nil {
		return nil, fmt.Errorf("load blocklist: %w", err)
	}
	client := backend.New(cfg.ServerBaseURL, cfg.DeviceID, cfg.DeviceToken)
	proxy := filter.NewProxy(cfg.ListenAddr, cfg.UpstreamDNS, list, client)

	if err := proxy.Start(); err != nil {
		return nil, fmt.Errorf("start dns proxy: %w", err)
	}
	// Point the system at our local resolver only once filtering is live.
	localIP := cfg.ListenAddr
	if h, _, ok := splitHostPort(localIP); ok {
		localIP = h
	}
	if err := sysdns.Redirect(localIP); err != nil {
		log.Printf("warning: could not redirect system DNS: %v", err)
	}

	c := &core{cfg: cfg, proxy: proxy, stop: make(chan struct{})}
	go c.heartbeatLoop(client)
	return c, nil
}

func (c *core) heartbeatLoop(client *backend.Client) {
	interval := time.Duration(c.cfg.HeartbeatSeconds) * time.Second
	t := time.NewTicker(interval)
	defer t.Stop()
	if err := client.Heartbeat(); err != nil {
		log.Printf("initial heartbeat: %v", err)
	}
	for {
		select {
		case <-c.stop:
			return
		case <-t.C:
			if err := client.Heartbeat(); err != nil {
				log.Printf("heartbeat: %v", err)
			}
			client.Flush()
		}
	}
}

func (c *core) shutdown() {
	close(c.stop)
	c.proxy.Stop()
	if err := sysdns.Restore(); err != nil {
		log.Printf("warning: could not restore system DNS: %v", err)
	}
}

func splitHostPort(addr string) (host, port string, ok bool) {
	for i := len(addr) - 1; i >= 0; i-- {
		if addr[i] == ':' {
			return addr[:i], addr[i+1:], true
		}
	}
	return addr, "", false
}

// --- svc.Handler ---

type handler struct{}

func (handler) Execute(args []string, r <-chan svc.ChangeRequest, status chan<- svc.Status) (bool, uint32) {
	const accepted = svc.AcceptStop | svc.AcceptShutdown
	status <- svc.Status{State: svc.StartPending}

	c, err := startCore()
	if err != nil {
		log.Printf("service start failed: %v", err)
		return true, 1
	}
	status <- svc.Status{State: svc.Running, Accepts: accepted}

	for req := range r {
		switch req.Cmd {
		case svc.Interrogate:
			status <- req.CurrentStatus
		case svc.Stop, svc.Shutdown:
			status <- svc.Status{State: svc.StopPending}
			c.shutdown()
			status <- svc.Status{State: svc.Stopped}
			return false, 0
		}
	}
	return false, 0
}

func IsService() (bool, error) { return svc.IsWindowsService() }

func RunService() error { return svc.Run(Name, handler{}) }

// RunConsole runs filtering in the foreground for local testing (Ctrl-C quits).
func RunConsole() error {
	c, err := startCore()
	if err != nil {
		return err
	}
	defer c.shutdown()
	log.Println("running in console mode; press Ctrl-C to stop")
	select {} // block forever
}

// Install registers the service to auto-start and to restart on failure.
func Install(exePath string) error {
	m, err := mgr.Connect()
	if err != nil {
		return err
	}
	defer m.Disconnect()

	if s, err := m.OpenService(Name); err == nil {
		s.Close()
		return fmt.Errorf("service %s already exists", Name)
	}
	s, err := m.CreateService(Name, exePath, mgr.Config{
		DisplayName: DisplayName,
		Description: Description,
		StartType:   mgr.StartAutomatic,
	})
	if err != nil {
		return err
	}
	defer s.Close()

	// Restart automatically if the process dies -- first line of tamper defense.
	if err := s.SetRecoveryActions([]mgr.RecoveryAction{
		{Type: mgr.ServiceRestart, Delay: 5 * time.Second},
		{Type: mgr.ServiceRestart, Delay: 5 * time.Second},
		{Type: mgr.ServiceRestart, Delay: 30 * time.Second},
	}, 86400); err != nil {
		log.Printf("warning: could not set recovery actions: %v", err)
	}
	return s.Start()
}

func Remove() error {
	m, err := mgr.Connect()
	if err != nil {
		return err
	}
	defer m.Disconnect()
	s, err := m.OpenService(Name)
	if err != nil {
		return fmt.Errorf("service not installed")
	}
	defer s.Close()
	_, _ = s.Control(svc.Stop)
	time.Sleep(2 * time.Second)
	return s.Delete()
}
