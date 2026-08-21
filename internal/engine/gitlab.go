package engine

import (
	"errors"
	"fmt"
	"strings"
)

// apiProjectURL addresses the project's REST API. GitLab takes either a numeric project id or
// the namespace/name path with the slash percent-encoded, and the path keeps this readable and
// stable if the project is ever recreated.
const apiProjectURL = RepoHost + "/api/v4/projects/" + RepoOwner + "%2F" + RepoName

// Asset is one file attached to a release.
type Asset struct {
	Name string
	URL  string
}

// Release is the slice of a release both front ends need.
type Release struct {
	TagName string
	Assets  []Asset
}

// gitlabRelease is the wire shape of the releases API. GitLab does not attach files to a
// release the way GitHub did: a release carries links, which the release job in
// .gitlab-ci.yml points at the generic package registry where the build jobs upload. Decoding
// into its own type keeps that shape out of the rest of the engine, which wants only a name
// and a URL per file.
type gitlabRelease struct {
	TagName string `json:"tag_name"`
	Assets  struct {
		Links []struct {
			Name string `json:"name"`
			URL  string `json:"url"`
		} `json:"links"`
	} `json:"assets"`
}

func (r gitlabRelease) release() *Release {
	release := &Release{TagName: r.TagName}
	for _, link := range r.Assets.Links {
		release.Assets = append(release.Assets, Asset{Name: link.Name, URL: link.URL})
	}
	return release
}

var errNoRelease = errors.New("no release found")

// LatestRelease reads the newest published release. GitLab's permalink/latest picks the most
// recent by release date and skips upcoming ones, so it needs no filtering here.
func LatestRelease() (*Release, error) {
	url := apiProjectURL + "/releases/permalink/latest"
	var wire gitlabRelease
	if err := getJSON(url, &wire); err != nil {
		if errors.Is(err, errNotFound) {
			// GitLab answers 404 both for a project with no releases and for one the caller
			// cannot see, so the message covers both rather than guessing.
			return nil, fmt.Errorf("%w: %s has no published releases yet, or is not readable without signing in", errNoRelease, RepoURL)
		}
		return nil, err
	}
	return wire.release(), nil
}

// ModAsset picks the mod jar out of a release. The release also links the installer and the
// launcher, so match on the mod's own artifact name and rule the rest out.
func ModAsset(release *Release) (*Asset, error) {
	for i := range release.Assets {
		name := strings.ToLower(release.Assets[i].Name)
		if !strings.HasSuffix(name, ".jar") {
			continue
		}
		if strings.Contains(name, "-sources") || strings.Contains(name, "-dev") ||
			strings.Contains(name, "installer") {
			continue
		}
		if strings.HasPrefix(name, modArtifactPrefix) {
			return &release.Assets[i], nil
		}
	}
	return nil, fmt.Errorf("release %s has no %s*.jar asset", release.TagName, modArtifactPrefix)
}

// LauncherAsset picks the desktop launcher binary for the given OS out of a release. CI names
// the launcher assets esdeath-launcher-windows-amd64.exe, esdeath-launcher-linux-amd64, and
// esdeath-launcher-macos.zip (see the launcher jobs in .gitlab-ci.yml), so match on the
// launcher name plus the OS token and rule out the installer and the mod jars. goos is a
// runtime.GOOS value; darwin maps to the "macos" token the CI asset uses.
func LauncherAsset(release *Release, goos string) (*Asset, error) {
	osToken := goos
	if goos == "darwin" {
		osToken = "macos"
	}
	for i := range release.Assets {
		name := strings.ToLower(release.Assets[i].Name)
		if !strings.Contains(name, "launcher") {
			continue
		}
		// The setup installer also carries "esdeath" and, on some names, would otherwise slip
		// through; it never contains "launcher", so the check above already excludes it. This
		// guard is belt-and-braces against a future asset that pairs the two words.
		if strings.Contains(name, "installer") || strings.Contains(name, "setup") {
			continue
		}
		if strings.Contains(name, osToken) {
			return &release.Assets[i], nil
		}
	}
	return nil, fmt.Errorf("release %s has no launcher asset for %s", release.TagName, goos)
}
