package engine

import (
	"encoding/json"
	"encoding/xml"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"time"
)

var httpClient = &http.Client{Timeout: 5 * time.Minute}

// userAgent identifies both front ends to GitHub, which rejects API requests that send none.
const userAgent = "esdeath-cryostasis"

var errNotFound = errors.New("not found")

// fetch performs a GET and hands back the body, leaving status handling in one place for
// every caller. The body must be closed by the caller.
func fetch(url, accept string) (io.ReadCloser, error) {
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", accept)
	req.Header.Set("User-Agent", userAgent)

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
	req.Header.Set("User-Agent", userAgent)

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
