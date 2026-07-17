//go:build windows

package main

import (
	"os"
	"syscall"
	"unsafe"
)

// The banner is Braille glyphs in truecolor ANSI, and a stock conhost gives neither by
// default: it renders escape codes literally and mangles UTF-8 unless the code page is
// switched. Both settings are per-console and reset when the process exits.
const (
	enableVirtualTerminalProcessing = 0x0004
	utf8CodePage                    = 65001
)

var kernel32 = syscall.NewLazyDLL("kernel32.dll")

func setupConsole() {
	kernel32.NewProc("SetConsoleOutputCP").Call(uintptr(utf8CodePage))

	handle := syscall.Handle(os.Stdout.Fd())
	var mode uint32
	getMode := kernel32.NewProc("GetConsoleMode")
	if ret, _, _ := getMode.Call(uintptr(handle), uintptr(unsafe.Pointer(&mode))); ret == 0 {
		return
	}
	setMode := kernel32.NewProc("SetConsoleMode")
	setMode.Call(uintptr(handle), uintptr(mode|enableVirtualTerminalProcessing))
}

// pause keeps the window open when the installer was double-clicked from Explorer, where
// the console dies with the process and the user would never see the result.
func pause() {
	if !launchedFromExplorer() {
		return
	}
	os.Stdout.WriteString("\n   Press Enter to close...")
	stdin.ReadString('\n')
}

// launchedFromExplorer reports whether this process owns its console alone. A shell that
// ran the binary is also attached, so a process list of one means Explorer spawned it.
func launchedFromExplorer() bool {
	proc := kernel32.NewProc("GetConsoleProcessList")
	var pids [4]uint32
	count, _, _ := proc.Call(uintptr(unsafe.Pointer(&pids[0])), uintptr(len(pids)))
	return count == 1
}
