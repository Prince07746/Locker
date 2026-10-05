//go:build !windows

package service

import "errors"

const Name = "LockerAgent"

var errUnsupported = errors.New("the Windows service is only available on Windows builds")

func IsService() (bool, error) { return false, nil }
func RunService() error        { return errUnsupported }
func RunConsole() error        { return errUnsupported }
func Install(exePath string) error { return errUnsupported }
func Remove() error               { return errUnsupported }
