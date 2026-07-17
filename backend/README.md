# Cryostasis backend (dev instance)

A local development implementation of the cosmetics and accounts backend described in
`../docs/backend-api.md`. It runs on the JDK's built-in HTTP server with only Gson as a
dependency, so no framework or database install is needed to test the client against it.

Production is expected to replace the storage layer (`Store`) with a real database plus
object storage for textures, and to fill in Microsoft OAuth token verification in
`CryostasisServer.authorized`. The routes and payload shapes do not change.

## Build and run

Requires a JDK 21. From this directory:

```
# Compile
javac -cp lib/gson-2.11.0.jar -d out $(find src -name "*.java")

# Run (open dev mode, no auth, port 8080)
java -cp "out:lib/gson-2.11.0.jar" moe.ramon.cryostasis.backend.CryostasisServer
```

On Windows use `out;lib/gson-2.11.0.jar` (semicolon) for the classpath separator.

Configuration through environment variables:

- `CRYOSTASIS_PORT` listen port, default `8080`.
- `CRYOSTASIS_DATA` JSON data file path, default `backend-data.json`.
- `CRYOSTASIS_TOKEN` if set, every mutating request must send `Authorization: Bearer <token>`.
  If unset, the server runs in open dev mode.

## Endpoints

See `../docs/backend-api.md` for the full contract. Quick check once running:

```
curl localhost:8080/api/version
curl localhost:8080/api/capes
curl -X POST localhost:8080/api/players/<uuid>/cosmetics -d '{"cosmetic":"wings"}'
curl localhost:8080/api/players/<uuid>/cosmetics
```
