package engine

import (
	"errors"
	"fmt"
	"strings"
)

// Asset is one file attached to a GitHub release.
type Asset struct {
	Name string `json:"name"`
	URL  string `json:"browser_download_url"`
	Size int64  `json:"size"`
}

// Release is the slice of a GitHub release both front ends need.
type Release struct {
	TagName string  `json:"tag_name"`
	Assets  []Asset `json:"assets"`
}

var errNoRelease = errors.New("no release found")

// LatestRelease reads the newest published (non-prerelease, non-draft) release. GitHub's
// /releases/latest already applies that filter for us.
func LatestRelease() (*Release, error) {
	url := fmt.Sprintf("https://api.github.com/repos/%s/%s/releases/latest", RepoOwner, RepoName)
	var release Release
	if err := getJSON(url, &release); err != nil {
		if errors.Is(err, errNotFound) {
			return nil, fmt.Errorf("%w: %s has no published releases yet", errNoRelease, RepoURL)
		}
		return nil, err
	}
	return &release, nil
}

// ModAsset picks the mod jar out of a release. The release also carries a sources jar and
// the installer binaries, so match on the mod's own artifact name and rule the rest out.
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
