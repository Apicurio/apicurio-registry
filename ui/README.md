## Description
This module is the user interface for Apicurio Registry.  It is a single page React application
using the following technology stack:

* Node/npm
* Typescript
* Vite
* Patternfly

The UI is broken up into multiple React applications that are composed together to provide the
full functionality.  This is done to separate concerns and more easily manage upgrade and CVEs.

The main UI component is `ui-app` and contains the bulk of the user experience.  Additional UI
components (for example `ui-docs`) provide specific functionality extracted and isolated for
reasons mentioned above.

## Building the UI
**Note**: You will need to have Node/NPM installed to work with the UI.  Version 16 or later of Node.js 
should be sufficient.

### Install Dependencies

```
npm install
```

This will result in dependencies being installed for all UI modules (e.g. `ui-app` **and** `ui-docs`).
You should see a `node_modules` in each directory if this completes successfully.

### Build
```
npm run build
```

This will transpile/build the code for all UI modules.  The result should be a `dist` directory in
each UI module.

### Package
```
npm run package
```

This will bundle up all of the UI modules into a single `dist` directory at the root of the `ui` 
directory.  This bundle is then suitable for including in e.g. a container image.

### Containerize
Once the UI is built and packaged, docker/podman can be used to build a container image.  This
can be done by using the included `build-docker.sh` script or with the following command (or
equivalent):

```
docker build -t="apicurio/apicurio-registry-ui:latest-snapshot" --rm .
```

## Running the Container Image
You can build the UI container image locally (see the steps above) or you can pull the latest
container image with this command:

```
docker pull apicurio/apicurio-registry-ui:latest-snapshot
```

Either way, you can run the UI using docker with the following command:

```
docker run -it -p 8888:8080 apicurio/apicurio-registry-ui:latest-snapshot
```

Running without passing any environment variables will result in sensible defaults for running
the UI locally against a default locally run backend.  However, the following environment
variables can be used to control the behavior of the UI:

| ENV var     | Description | Default |
| ----------- | ----------- | ------- |
| REGISTRY_API_URL | Location of the backend API. | http://localhost:8080/apis/registry/v3 |
| REGISTRY_AUTH_TYPE | Type of authentication to use. [none, oidc] | none |
| REGISTRY_AUTH_URL | URL of the OIDC server. | "" |
| REGISTRY_AUTH_CLIENT_ID | Client ID for auth using OIDC. | registry-ui |
| REGISTRY_AUTH_CLIENT_SCOPES | Scopes to request for the OIDC client. | openid profile email offline_token |
| REGISTRY_AUTH_REDIRECT_URL | Redirect URL when authenticating using OIDC. | http://localhost:8888 |
| REGISTRY_AUTH_RBAC_ENABLED | Enable role based access control. | false |
| REGISTRY_AUTH_OBAC_ENABLED | Enable owner based access control. | false |
| REGISTRY_FEATURE_READ_ONLY | Enable read-only mode for the UI only. | false |
| REGISTRY_FEATURE_BREADCRUMBS | Show breadcrumbs in the UI. | true |
| REGISTRY_FEATURE_ROLE_MANAGEMENT | Enable/show the Access tab in the UI. | false |
| REGISTRY_FEATURE_SETTINGS | Enable/show the Settings tab in the UI. | true |


## Developing the UI

If you would like to contribute code changes to the UI, you will want to run the UI application(s)
in DEV mode.

**Warning**: For the dev version of the UI to work, you will need two files that are not checked into
version control:  `config.js` and `version.js`.  To ensure you have these files, run the `init-dev.sh`
script from the `ui-app` directory.  For more details see [ui-app/README.md](ui-app/README.md).

```
npm run dev
```

This will start the UI in dev mode, hosted (by default) on port 8888.  When running successfully,
you should see output similar to:

```
  VITE v4.4.11  ready in 149 ms

  ➜  Local:   http://127.0.0.1:8888/
  ➜  Network: use --host to expose
  ➜  press h to show help
```

You can then access the UI on port 8888 or your localhost.

**Note**:  you will need the REST API running for the UI to work.  See the README at the root of
this repository for examples of how to do that.


## Testing
Whenever changes are made to the UI, it is helpful to add new tests to the [Playwright-based](https://playwright.dev/)
test suite.  The test suite can be found in the `tests` directory.

You can run the tests like this:

```
cd tests
npm install
npm run test
```

The tests assume that the UI is running on localhost port 8888.

Note that you need to have Playwright installed for this to work.  Typically you can install
Playwright with the following command:

```
npx playwright install
```