# OpenBoxes Docker
An overview of how Docker is used within OpenBoxes.

## Docker compose files
We provide the following compose files for running the application under different setups. You can switch between them with the `-f` commandline arg. If you don't specify which file to use, `docker-compose.yml` will be used.

- `docker-compose-base.yml` - Not to be run directly. Contains common configuration that is shared between all compose files.
- `docker-compose.yml` - For running the app with a bundled, containerized database.
- `docker-compose-hostdb.yml` - For running the app against a non-containerized database that is running directly on the host machine
- `docker-compose-remotedb.yml` - For running the app against a database that is externally managed on a separate host
- `docker-compose-dev.yml` - For developing: runs the app from source with live reloading. See [Development loop](#development-loop).

## Running OpenBoxes via Docker (compose)
The following outlines how to run a containerized instance of OpenBoxes.

These compose files support running against officially released OpenBoxes images and locally built ones. See the community guide for [instructions on creating your own Docker image of the application](https://openboxes.gitbook.io/contributor-guide/release-planning/creating-a-new-docker-image).

### Prerequisites
1. Install [Docker](https://docs.docker.com/) and [Docker Compose](https://docs.docker.com/compose/) (we support Docker Engine v24 or later)
2. [An openboxes database with a valid db user](https://openboxes.gitbook.io/contributor-guide/software-development/onboarding/running-openboxes-locally#id-2.-configure-the-database-instance)

### Steps
1. Start Docker
2. Navigate to the `/docker` directory
3. Optionally create a `.env` file containing any environment variable overrides that you need. See `.env.example` for a reference. If you do not provide an override for a variable, the default will be used. For example, given a line like `{DATASOURCE_USERNAME:-openboxes}` in the compose file, if `DATASOURCE_USERNAME` is not defined in your .env file, or it is blank, a value of `openboxes` will be used.
4. Run `docker compose up` to start your containers. You can select the compose file to use by specifying the `-f <config file>` argument. For example: `docker compose -f docker-compose-hostdb.yml up`
5. Look in `docker-compose`'s logs for `Grails application running at http:/localhost:8080/openboxes in environment: production` and connect to that URL

## Building an image from source (any architecture)
`Dockerfile.source` compiles the WAR inside a container and then builds the same runtime image as `Dockerfile`. It
needs no JDK or Node on the host, and works on arm64 machines such as Apple Silicon Macs, where the Node version
pinned by the build has no native macOS binary.

From the repository root, run:

```
docker build -f docker/Dockerfile.source -t openboxes/openboxes:latest .
```

Then follow the steps above, setting `OB_VERSION=latest` and a blank `OB_IMAGE_REPOSITORY=` in your `.env` file so
that compose uses the locally built image.

## Development loop
`docker-compose-dev.yml` runs OpenBoxes from your working copy, so changes show up without building an image. Like
`Dockerfile.source`, it needs no JDK or Node on the host. It starts three containers:

- `web` runs `npm run watch`, rebuilding the React and CSS bundles into `src/main/webapp/webpack/` whenever you save.
- `app` runs `./gradlew bootRun` (Grails development mode) on port 8080. It serves those bundles and GSP views from
  disk and reloads most Groovy changes. A Java debugger can attach on port 5005 (set `DEBUG_ENABLED=false` to turn
  this off).
- `db` is the same database service, with the same data, as `docker-compose.yml`.

From the `/docker` directory, run:

```
docker compose -f docker-compose-dev.yml up
```

The first start downloads Gradle and npm dependencies, which can take 10 minutes or more. They are kept in Docker
volumes, so later starts are much faster. npm dependencies are reinstalled only when `package-lock.json` changes.

| You change | What happens |
|---|---|
| React or CSS in `src/js` | `web` rebuilds in a few seconds; reload the page |
| GSP views | picked up on the next page load |
| Groovy controllers and services | usually reloaded automatically; otherwise `docker compose -f docker-compose-dev.yml restart app` |
| Domain classes, configuration or dependencies | `docker compose -f docker-compose-dev.yml restart app` |

Notes:

- This file uses the same compose project as `docker-compose.yml`, so starting it replaces the production-image app
  container, and running `docker compose up` again switches back. Only one of them can use port 8080 at a time.
- The app runs Liquibase migrations from your checked-out branch against the shared database. A branch with newer
  migrations will apply them to your existing data. Point `DATASOURCE_URL` at a separate database to avoid this.
- If changes made on the host are not being picked up, set `WATCHPACK_POLLING=true` in your `.env` file.
- Development mode is not production. Check important changes with an image built from `Dockerfile.source`.
