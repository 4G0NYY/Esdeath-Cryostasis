package main

import (
	"encoding/json"
	"encoding/xml"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

var httpClient = &http.Client{Timeout: 5 * time.Minute}

type ghAsset struct {
	Name string `json:"name"`
	URL  string `json:"browser_download_url"`
	Size int64  `json:"size"`
}

type ghRelease struct {
	TagName string    `json:"tag_name"`
	Assets  []ghAsset `json:"assets"`
}

var errNoRelease = errors.New("no release found")

// latestRelease reads the newest published (non-prerelease, non-draft) release. GitHub's
// /releases/latest already applies that filter for us.
func latestRelease() (*ghRelease, error) {
	url := fmt.Sprintf("https://api.github.com/repos/%s/%s/releases/latest", repoOwner, repoName)
	var release ghRelease
	if err := getJSON(url, &release); err != nil {
		if errors.Is(err, errNotFound) {
			return nil, fmt.Errorf("%w: %s has no published releases yet", errNoRelease, repoURL)
		}
		return nil, err
	}
	return &release, nil
}

// modAsset picks the mod jar out of a release. The release also carries a sources jar and
// the installer binaries, so match on the mod's own artifact name and rule the rest out.
func modAsset(release *ghRelease) (*ghAsset, error) {
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

var errNotFound = errors.New("not found")

// fetch performs a GET and hands back the body, leaving status handling in one place for
// every caller. The body must be closed by the caller.
func fetch(url, accept string) (io.ReadCloser, error) {
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", accept)
	// GitHub rejects API requests without a User-Agent outright.
	req.Header.Set("User-Agent", "esdeath-cryostasis-installer")

	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, err
	}

	if resp.StatusCode != http.StatusOK {
		resp.Body.Close()
		switch {
		case resp.StatusCode == http.StatusNotFound:
			return nil, errNotFound
		case resp.StatusCode == http.StatusForbidden && resp.Header.Get("X-RateLimit-Remaining") == "0":
			return nil, errors.New("GitHub rate limit reached for this IP, try again in an hour")
		default:
			return nil, fmt.Errorf("GET %s returned %s", url, resp.Status)
		}
	}
	return resp.Body, nil
}

func getJSON(url string, target any) error {
	body, err := fetch(url, "application/vnd.github+json")
	if err != nil {
		return err
	}
	defer body.Close()
	return json.NewDecoder(body).Decode(target)
}

func getXML(url string, target any) error {
	body, err := fetch(url, "application/xml")
	if err != nil {
		return err
	}
	defer body.Close()
	return xml.NewDecoder(body).Decode(target)
}

// downloadFile writes a URL to disk through a temp file in the destination directory, so a
// half-finished download can never be left behind under the real name. Staging in the same
// directory keeps the final step a rename on one filesystem.
func downloadFile(url, dest string) error {
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", "esdeath-cryostasis-installer")

	resp, err := httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("GET %s returned %s", url, resp.Status)
	}

	if err := os.MkdirAll(filepath.Dir(dest), 0o755); err != nil {
		return err
	}
	tmp, err := os.CreateTemp(filepath.Dir(dest), ".esdeath-download-*")
	if err != nil {
		return err
	}
	tmpName := tmp.Name()
	defer os.Remove(tmpName)

	if _, err := io.Copy(tmp, resp.Body); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return os.Rename(tmpName, dest)
}
