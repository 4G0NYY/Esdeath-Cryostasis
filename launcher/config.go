package main

import (
	"encoding/json"
	"errors"
	"net/url"
	"os"
	"path/filepath"
	"strings"
)

// configSchemaVersion is bumped whenever the on-disk shape changes. It is written into every
// file so an older config can be recognised and migrated forward rather than silently
// misread, which the project's ground rules require of all configuration.
const configSchemaVersion = 1

// DefaultBackendURL is the hosted instance. Self-hosters override it in settings; this is the
// only backend detail baked into the launcher, and it is a default, not a hardcode, precisely
// so pointing the client at another instance is a one-field change.
const DefaultBackendURL = "https://cryostasis.ramon.moe/api"

// Config is the launcher's persisted settings. Every field is optional at rest: an empty
// MinecraftDir means "find it", an empty MinecraftVersion means the engine default, so a
// hand-cleared file still loads.
type Config struct {
	SchemaVersion    int    `json:"schemaVersion"`
	BackendURL       string `json:"backendUrl"`
	MinecraftDir     string `json:"minecraftDir"`
	MinecraftVersion string `json:"minecraftVersion"`
}

// configPath is <user config dir>/EsdeathCryostasis/launcher.json, the per-OS config location
// (AppData on Windows, ~/Library/Application Support on macOS, ~/.config on Linux).
func configPath() (string, error) {
	dir, err := os.UserConfigDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "EsdeathCryostasis", "launcher.json"), nil
}

// LoadConfig reads the config, applying defaults for anything missing so callers never see a
// half-filled struct. A missing file is not an error: it is a first run, and the defaults are
// the answer.
func LoadConfig() (*Config, error) {
	path, err := configPath()
	if err != nil {
		return nil, err
	}
	raw, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return withDefaults(&Config{}), nil
	}
	if err != nil {
		return nil, err
	}

	var cfg Config
	if err := json.Unmarshal(raw, &cfg); err != nil {
		return nil, err
	}
	migrate(&cfg)
	return withDefaults(&cfg), nil
}

// Save normalises then writes the config. Normalising on the way in means a value entered in
// the GUI is stored in the exact form the launcher will later use, so what is saved is what
// takes effect.
func (c *Config) Save() error {
	c.normalise()
	path, err := configPath()
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	out, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return err
	}
	return writeFileAtomic(path, out)
}

// migrate brings an older file up to the current schema. There is only one version so far, so
// this just stamps the version; the switch is here so the next change has an obvious home and
// old files keep loading instead of being rejected.
func migrate(c *Config) {
	if c.SchemaVersion == configSchemaVersion {
		return
	}
	// switch c.SchemaVersion { case 1: ... } goes here as the shape evolves.
	c.SchemaVersion = configSchemaVersion
}

func withDefaults(c *Config) *Config {
	if c.SchemaVersion == 0 {
		c.SchemaVersion = configSchemaVersion
	}
	if strings.TrimSpace(c.BackendURL) == "" {
		c.BackendURL = DefaultBackendURL
	}
	c.normalise()
	return c
}

// normalise trims the backend URL and drops a trailing slash, so the client sees the same
// base whether the user typed "https://host/api" or "https://host/api/". The client appends
// path segments to it, and a doubled slash would break those requests.
func (c *Config) normalise() {
	c.BackendURL = strings.TrimRight(strings.TrimSpace(c.BackendURL), "/")
	c.MinecraftDir = strings.TrimSpace(c.MinecraftDir)
	c.MinecraftVersion = strings.TrimSpace(c.MinecraftVersion)
}

// validate reports why a config cannot be used, or nil when it is fine. Only the backend URL
// can really be wrong: the Minecraft fields are validated against the filesystem elsewhere.
func (c *Config) validate() error {
	u, err := url.Parse(c.BackendURL)
	if err != nil || (u.Scheme != "http" && u.Scheme != "https") || u.Host == "" {
		return errors.New("backend URL must be an http or https address, for example https://cryostasis.ramon.moe/api")
	}
	return nil
}
