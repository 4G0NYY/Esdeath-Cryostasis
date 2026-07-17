//go:build !windows

package main

// Terminals on Linux and macOS already speak ANSI and UTF-8, and the binary is run from a
// shell that outlives it, so neither setup nor a pause is needed.
func setupConsole() {}

func pause() {}
