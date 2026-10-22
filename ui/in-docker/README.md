# Running UI Tests in Docker

This folder contains Docker configuration files to run Playwright UI tests in a containerized environment.

## Purpose

Playwright, which is used for running the UI tests in this project, has limited operating system support and is officially only supported on Ubuntu. Developers using other Linux distributions (such as Fedora) or operating systems where Playwright is not natively supported can use this Docker-based solution to run the UI tests.

## What's Included

- **Dockerfile**: Defines an Ubuntu-based container with Playwright and its dependencies pre-installed
- **entrypoint.sh**: A startup script that runs initialization commands when the container starts
- **in-docker.sh**: A convenience script that builds the Docker image and runs the container with the appropriate volume mounts and network configuration

## How It Works

The Docker setup:

1. Uses Ubuntu as the base image (where Playwright is fully supported)
2. Installs Node.js, npm, and other required dependencies
3. Installs Playwright globally and its browser dependencies
4. Runs an entrypoint script on startup that:
   - Automatically installs npm dependencies for tests and ui-app if needed
   - Ensures Playwright browsers are installed and up-to-date
   - Displays helpful information about available commands
5. Mounts the project directory into the container so you can work with your local files
6. Uses host networking to allow the container to access locally running services
7. Runs with your user ID to avoid permission issues with generated files

## Usage

### Prerequisites

- Docker must be installed and running on your system
- The Apicurio Registry backend should be running locally (typically on localhost, port 8080)

### Running the Container

1. Run the setup script from anywhere in the project:
   ```bash
   ./in-docker/in-docker.sh
   ```

   Or navigate to the `in-docker` directory first:
   ```bash
   cd in-docker
   ./in-docker.sh
   ```

   This will:
   - Build the Docker image (if not already built)
   - Start a container with an interactive bash shell
   - Mount the project root directory to `/mnt/apicurio-registry-ui` inside the container

### Running Tests Inside the Container

Once inside the container, the entrypoint script will automatically handle dependency installation and setup. You can immediately run the UI tests:

1. Navigate to the tests directory:
   ```bash
   cd tests
   ```

2. Run the Playwright tests:
   ```bash
   npx playwright test
   ```

   By default, tests will run against `http://localhost:8888`. To run tests against a different URL, set the `REGISTRY_UI_URL` environment variable:
   ```bash
   REGISTRY_UI_URL=http://localhost:3000 npx playwright test
   ```

3. To view test reports:
   ```bash
   npx playwright show-report
   ```

**Note**: The entrypoint script automatically installs npm dependencies and Playwright browsers on first run, so manual installation steps are no longer required.

### Exiting the Container

Simply type `exit` or press `Ctrl+D` to leave the container. The container will be automatically removed after you exit.

## Troubleshooting

- **Permission Issues**: The container runs with your user ID to prevent permission problems with generated files
- **Network Access**: The container uses host networking, so it can access services running on your local machine

## Alternative: Native Installation

If you're using Ubuntu or another supported system, you may prefer to install Playwright directly on your host system instead of using Docker. Refer to the main project README or the `tests/` directory for native installation instructions.
