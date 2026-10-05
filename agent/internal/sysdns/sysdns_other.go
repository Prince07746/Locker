//go:build !windows

package sysdns

import "errors"

var errUnsupported = errors.New("system DNS redirection is implemented for Windows in this build")

func Redirect(localIP string) error { return errUnsupported }
func Restore() error                { return errUnsupported }
