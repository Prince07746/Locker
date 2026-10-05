package sysdns

import (
	"fmt"
	"net"
	"os/exec"
	"strings"
)

// activeInterfaces returns friendly names of up, non-loopback interfaces.
// On Windows these align with the names netsh expects.
func activeInterfaces() ([]string, error) {
	ifaces, err := net.Interfaces()
	if err != nil {
		return nil, err
	}
	var names []string
	for _, i := range ifaces {
		if i.Flags&net.FlagUp == 0 || i.Flags&net.FlagLoopback != 0 {
			continue
		}
		names = append(names, i.Name)
	}
	return names, nil
}

func netsh(args ...string) error {
	out, err := exec.Command("netsh", args...).CombinedOutput()
	if err != nil {
		return fmt.Errorf("netsh %s: %v (%s)", strings.Join(args, " "), err, strings.TrimSpace(string(out)))
	}
	return nil
}

// Redirect points every active interface's DNS at the local resolver
// (expects the IP, e.g. "127.0.0.1").
func Redirect(localIP string) error {
	names, err := activeInterfaces()
	if err != nil {
		return err
	}
	var firstErr error
	for _, n := range names {
		if e := netsh("interface", "ipv4", "set", "dnsservers",
			fmt.Sprintf("name=%s", n), "static", localIP, "primary"); e != nil && firstErr == nil {
			firstErr = e
		}
	}
	return firstErr
}

// Restore returns interfaces to DHCP-provided DNS. NOTE: if the machine
// previously used a *static* DNS, that original value is not preserved in this
// v1 -- restoring to DHCP is the common reset. Capture-and-restore of prior
// static servers is a planned enhancement.
func Restore() error {
	names, err := activeInterfaces()
	if err != nil {
		return err
	}
	var firstErr error
	for _, n := range names {
		if e := netsh("interface", "ipv4", "set", "dnsservers",
			fmt.Sprintf("name=%s", n), "dhcp"); e != nil && firstErr == nil {
			firstErr = e
		}
	}
	return firstErr
}
