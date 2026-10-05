package main

import (
	"bufio"
	"fmt"
	"log"
	"os"
	"path/filepath"

	"github.com/Prince07746/Locker/agent/internal/config"
	"github.com/Prince07746/Locker/agent/internal/service"
	"github.com/Prince07746/Locker/agent/internal/sysdns"
	"github.com/Prince07746/Locker/agent/internal/uninstall"
)

const version = "1.0.0"

func main() {
	setupLogging()

	// When the Windows Service Control Manager launches us, there are no
	// meaningful CLI args and we must hand control to the service harness.
	if isSvc, _ := service.IsService(); isSvc {
		if err := service.RunService(); err != nil {
			log.Fatalf("service run: %v", err)
		}
		return
	}

	if len(os.Args) < 2 {
		usage()
		return
	}

	switch os.Args[1] {
	case "install-service":
		exe, _ := os.Executable()
		if err := service.Install(exe); err != nil {
			fatal(err)
		}
		fmt.Println("Service installed and started.")

	case "remove-service":
		if err := service.Remove(); err != nil {
			fatal(err)
		}
		fmt.Println("Service removed.")

	case "uninstall":
		// Guardian-authorized removal. Exit non-zero if denied so the
		// installer aborts and leaves protection in place.
		in := bufio.NewReader(os.Stdin)
		if err := uninstall.Authorize(in, os.Stdout); err != nil {
			fmt.Fprintln(os.Stderr, "Removal not authorized:", err)
			os.Exit(1)
		}
		_ = service.Remove()
		_ = sysdns.Restore()
		fmt.Println("Protection removed.")

	case "console", "run":
		if err := service.RunConsole(); err != nil {
			fatal(err)
		}

	case "version":
		fmt.Println("locker-agent", version)

	default:
		usage()
	}
}

func setupLogging() {
	_ = os.MkdirAll(config.Dir(), 0o755)
	f, err := os.OpenFile(filepath.Join(config.Dir(), "agent.log"),
		os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o640)
	if err == nil {
		log.SetOutput(f)
	}
	log.SetFlags(log.LstdFlags | log.LUTC)
}

func usage() {
	fmt.Println(`locker-agent — Content Guardian device agent

Usage:
  locker-agent install-service   Register and start the Windows service
  locker-agent remove-service    Stop and delete the service (no authorization)
  locker-agent uninstall         Guardian-authorized removal (prompts for code)
  locker-agent console           Run filtering in the foreground (testing)
  locker-agent version           Print version

Configuration lives at %ProgramData%\Locker\config.json`)
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr, "error:", err)
	os.Exit(1)
}
