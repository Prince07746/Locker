// Package uninstall runs the guardian-authorized removal flow. It is called by
// the `uninstall` command, which the installer wires into Add/Remove Programs
// so that normal removal cannot bypass it.
package uninstall

import (
	"bufio"
	"fmt"
	"os"
	"strings"

	"github.com/Prince07746/Locker/agent/internal/backend"
	"github.com/Prince07746/Locker/agent/internal/config"
)

// Authorize runs steps 1 and 2 of the server flow. Returns nil only when the
// server approves removal. A non-nil error means removal must NOT proceed.
func Authorize(in *bufio.Reader, out *os.File) error {
	cfg, err := config.Load()
	if err != nil {
		return fmt.Errorf("cannot read config: %w", err)
	}
	client := backend.New(cfg.ServerBaseURL, cfg.DeviceID, cfg.DeviceToken)

	fmt.Fprintln(out, "Requesting removal authorization...")
	attemptID, err := client.RequestUninstall()
	if err != nil {
		return fmt.Errorf("could not reach server: %w", err)
	}
	fmt.Fprintln(out, "A one-time code has been emailed to the guardian.")
	fmt.Fprint(out, "Enter the authorization code: ")

	line, err := in.ReadString('\n')
	if err != nil {
		return fmt.Errorf("no code entered")
	}
	code := strings.TrimSpace(line)
	if code == "" {
		return fmt.Errorf("no code entered")
	}

	if err := client.VerifyUninstall(attemptID, code); err != nil {
		return fmt.Errorf("authorization denied: %w", err)
	}
	fmt.Fprintln(out, "Authorized.")
	return nil
}
