// Command launcher is the Esdeath: Cryostasis desktop launcher. It wraps the shared install
// engine (internal/engine) in a Wails desktop app: install and update the mod, choose which
// backend the client talks to, and hand off to the official Minecraft launcher to play.
//
// The backend choice is the reason this exists over the plain installer: the client reads its
// backend from the cryostasis.api system property, and the launcher pins that into the Esdeath
// profile's javaArgs, so a self-hoster can point the client at their own instance from the UI.
package main

import (
	"embed"

	"github.com/wailsapp/wails/v2"
	"github.com/wailsapp/wails/v2/pkg/options"
	"github.com/wailsapp/wails/v2/pkg/options/assetserver"
)

//go:embed all:frontend/dist
var assets embed.FS

func main() {
	app := NewApp()

	// The window background is the theme's darkest navy (Theme.BG_BOTTOM), so the frame matches
	// the client's look before the frontend has even painted.
	err := wails.Run(&options.App{
		Title:            "Esdeath: Cryostasis Launcher",
		Width:            1000,
		Height:           660,
		MinWidth:         840,
		MinHeight:        560,
		BackgroundColour: &options.RGBA{R: 5, G: 9, B: 15, A: 255},
		AssetServer:      &assetserver.Options{Assets: assets},
		OnStartup:        app.startup,
		Bind:             []any{app},
	})
	if err != nil {
		println("Error:", err.Error())
	}
}
