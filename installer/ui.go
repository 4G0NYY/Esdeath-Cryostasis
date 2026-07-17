package main

import (
	"bufio"
	_ "embed"
	"fmt"
	"os"
	"strings"

	"github.com/4G0NYY/Esdeath-Cryostasis/internal/engine"
)

//go:embed assets/esdeath-ascii.txt
var asciiArt string

// cliProgress adapts the engine's Progress seam onto the installer's colored line printers, so
// the shared engine reports detail through the same look as every other line this tool prints.
type cliProgress struct{}

func (cliProgress) Info(format string, args ...any) { info(format, args...) }
func (cliProgress) Warn(format string, args ...any) { warn(format, args...) }

// ANSI sequences. Written as truecolor because the banner is a single flat ice-blue and
// the 16-color palette has nothing close enough to it.
const (
	reset    = "\x1b[0m"
	bold     = "\x1b[1m"
	iceBlue  = "\x1b[38;2;134;208;255m"
	deepBlue = "\x1b[38;2;90;143;199m"
	dimGray  = "\x1b[38;2;140;150;165m"
	red      = "\x1b[38;2;235;110;110m"
	green    = "\x1b[38;2;120;220;150m"
	yellow   = "\x1b[38;2;235;200;120m"
)

var stdin = bufio.NewReader(os.Stdin)

func banner() {
	fmt.Print(iceBlue)
	for _, line := range strings.Split(strings.TrimRight(asciiArt, "\n"), "\n") {
		fmt.Println(strings.TrimRight(line, "\r"))
	}
	fmt.Print(reset)
	fmt.Println()
	fmt.Printf("  %s%sEsdeath: Cryostasis Installer%s\n", bold, iceBlue, reset)
	fmt.Printf("  %s%s%s\n", deepBlue, engine.RepoURL, reset)
	fmt.Println()
}

func step(format string, args ...any) {
	fmt.Printf("%s::%s %s\n", iceBlue, reset, fmt.Sprintf(format, args...))
}

func ok(format string, args ...any) {
	fmt.Printf("   %s+%s %s\n", green, reset, fmt.Sprintf(format, args...))
}

func warn(format string, args ...any) {
	fmt.Printf("   %s!%s %s\n", yellow, reset, fmt.Sprintf(format, args...))
}

func info(format string, args ...any) {
	fmt.Printf("     %s%s%s\n", dimGray, fmt.Sprintf(format, args...), reset)
}

// confirm asks a yes/no question. Defaults to yes on an empty line, since every prompt in
// this installer is a step the user already opted into by running it.
func confirm(question string, assumeYes bool) bool {
	if assumeYes {
		fmt.Printf("   %s?%s %s [Y/n] y (auto)\n", iceBlue, reset, question)
		return true
	}
	for {
		fmt.Printf("   %s?%s %s [Y/n] ", iceBlue, reset, question)
		line, err := stdin.ReadString('\n')
		if err != nil {
			// No console to read from (piped or double-clicked without a tty): treat it as a
			// decline rather than silently modifying the user's install.
			fmt.Println()
			return false
		}
		switch strings.ToLower(strings.TrimSpace(line)) {
		case "", "y", "yes":
			return true
		case "n", "no":
			return false
		}
	}
}
