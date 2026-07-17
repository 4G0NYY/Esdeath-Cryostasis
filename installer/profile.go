package main

import (
	"bytes"
	_ "embed"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"image"
	"image/color"
	"image/png"
	"os"
	"path/filepath"
	"time"
)

//go:embed assets/logo.png
var logoPNG []byte

// The launcher renders profile icons at 32px. 128 keeps it crisp on a HiDPI display while
// holding the base64 blob to a few tens of KB, since it is inlined into a JSON file the
// launcher rewrites constantly.
const iconSize = 128

const launcherTimeFormat = "2006-01-02T15:04:05.000Z"

// upsertProfile creates or refreshes the Esdeath profile. The whole file is round-tripped
// through a generic map rather than a typed struct so that other profiles and any launcher
// fields this installer does not model survive the rewrite untouched.
func upsertProfile(mcDir, versionID string) (created bool, err error) {
	path := filepath.Join(mcDir, "launcher_profiles.json")
	raw, err := os.ReadFile(path)
	if err != nil {
		return false, err
	}

	var root map[string]any
	if err := json.Unmarshal(raw, &root); err != nil {
		return false, fmt.Errorf("launcher_profiles.json is not valid JSON: %w", err)
	}

	profiles, _ := root["profiles"].(map[string]any)
	if profiles == nil {
		profiles = map[string]any{}
		root["profiles"] = profiles
	}

	icon, err := profileIcon()
	if err != nil {
		return false, err
	}

	now := time.Now().UTC().Format(launcherTimeFormat)
	profile, existed := profiles[profileKey].(map[string]any)
	if !existed {
		profile = map[string]any{"created": now}
	}
	profile["name"] = profileName
	profile["type"] = "custom"
	profile["lastVersionId"] = versionID
	profile["icon"] = icon
	profiles[profileKey] = profile

	out, err := json.MarshalIndent(root, "", "  ")
	if err != nil {
		return false, err
	}
	return !existed, writeFileAtomic(path, out)
}

// profileIcon returns the logo as the data URI the launcher accepts in place of one of its
// built-in icon names.
func profileIcon() (string, error) {
	src, err := png.Decode(bytes.NewReader(logoPNG))
	if err != nil {
		return "", fmt.Errorf("decoding the bundled logo: %w", err)
	}
	var buf bytes.Buffer
	if err := png.Encode(&buf, downscale(src, iconSize)); err != nil {
		return "", err
	}
	return "data:image/png;base64," + base64.StdEncoding.EncodeToString(buf.Bytes()), nil
}

// downscale box-filters an image to a square of the given size. Averaging each source
// region beats nearest-neighbour on the logo's thin glowing outline, which sampling alone
// breaks into dashes. Hand-rolled because the standard library ships no resampler and this
// installer is deliberately dependency free.
//
// Works in premultiplied space throughout: RGBA() hands back premultiplied values, and
// averaging those is what makes a transparent edge fade out instead of bleeding the
// backdrop into it. The destination is image.RGBA for the same reason, since it stores
// premultiplied samples and needs no conversion back.
func downscale(src image.Image, size int) image.Image {
	bounds := src.Bounds()
	dst := image.NewRGBA(image.Rect(0, 0, size, size))

	for y := 0; y < size; y++ {
		y0 := bounds.Min.Y + y*bounds.Dy()/size
		y1 := bounds.Min.Y + (y+1)*bounds.Dy()/size
		if y1 <= y0 {
			y1 = y0 + 1
		}
		for x := 0; x < size; x++ {
			x0 := bounds.Min.X + x*bounds.Dx()/size
			x1 := bounds.Min.X + (x+1)*bounds.Dx()/size
			if x1 <= x0 {
				x1 = x0 + 1
			}

			var r, g, b, a, n uint64
			for sy := y0; sy < y1; sy++ {
				for sx := x0; sx < x1; sx++ {
					sr, sg, sb, sa := src.At(sx, sy).RGBA()
					r += uint64(sr)
					g += uint64(sg)
					b += uint64(sb)
					a += uint64(sa)
					n++
				}
			}
			dst.Set(x, y, color.RGBA{
				R: uint8(r / n >> 8),
				G: uint8(g / n >> 8),
				B: uint8(b / n >> 8),
				A: uint8(a / n >> 8),
			})
		}
	}
	return dst
}

// writeFileAtomic replaces a file through a rename. launcher_profiles.json holds every
// profile the user owns, so a crash mid-write must not be able to truncate it.
func writeFileAtomic(path string, data []byte) error {
	tmp, err := os.CreateTemp(filepath.Dir(path), ".esdeath-tmp-*")
	if err != nil {
		return err
	}
	tmpName := tmp.Name()
	defer os.Remove(tmpName)

	if _, err := tmp.Write(data); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return os.Rename(tmpName, path)
}
