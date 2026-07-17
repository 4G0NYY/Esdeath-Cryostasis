package engine

import (
	"fmt"
	"strings"
)

// Fabric API ships from Fabric's own maven, which is the same artifact the mod is compiled
// against and the same host the loader installer comes from. Using it keeps the installer off
// third-party mod hosts and their rate limits.
const fabricApiMavenBase = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/"

// mavenMetadata is the slice of maven-metadata.xml this installer reads.
type mavenMetadata struct {
	Versioning struct {
		Versions []string `xml:"versions>version"`
	} `xml:"versioning"`
}

// latestFabricApi resolves the newest Fabric API build for a Minecraft version, returning the
// version, its jar URL, and the filename to save it under.
func latestFabricApi(mcVersion string) (version, url, filename string, err error) {
	var meta mavenMetadata
	if err := getXML(fabricApiMavenBase+"maven-metadata.xml", &meta); err != nil {
		return "", "", "", fmt.Errorf("fetching the Fabric API version list: %w", err)
	}

	version = pickFabricApiVersion(meta.Versioning.Versions, mcVersion)
	if version == "" {
		return "", "", "", fmt.Errorf("Fabric API has no build for Minecraft %s", mcVersion)
	}

	filename = "fabric-api-" + version + ".jar"
	// The '+' stays literal: it separates the version's path segments here, and only means a
	// space in a query string, never in a path.
	return version, fabricApiMavenBase + version + "/" + filename, filename, nil
}

// pickFabricApiVersion chooses the newest build for a Minecraft version out of a maven
// version list. Fabric API versions are <api>+<minecraft>, so the game version is an exact
// suffix match and only the API part is compared.
func pickFabricApiVersion(versions []string, mcVersion string) string {
	suffix := "+" + mcVersion
	best := ""
	for _, candidate := range versions {
		if !strings.HasSuffix(candidate, suffix) {
			continue
		}
		if best == "" || compareVersions(strings.TrimSuffix(candidate, suffix),
			strings.TrimSuffix(best, suffix)) > 0 {
			best = candidate
		}
	}
	return best
}
