# Apicurio Registry UI

Apicurio Registry React based Single Page Application based on Patternfly 4

## Requirements
This project requires node version 10.x.x and npm version 6.x.x.  It also uses the yarn package manager (version 1.13.0 or higher). 
Prior to building this project make sure you have these applications installed.  After installing node and npm you 
can install yarn globally by typing:

`npm install yarn -g'

## Development Scripts

Install development/build dependencies
`yarn install`

Run a full build
`yarn build`

Initialize config.js
`./init-dev.sh none`

Note: the init-dev.sh script just copies an appropriate file from config/config-*.js to the right place.  You can either specify `none` or `keycloakjs` as the argument to the script.  The choice depends on how you are running the back-end component.

Start the development server
`yarn start`

Once the development server is running you can access the UI via http://localhost:8888

Note that you will need a registry back-end running for the UI to actually work.  The easiest way to do this is using 
docker, but you could also run the registry from maven or any other way you choose.  Here is how you do it with Docker:

`docker run -it -p 8080:8080 apicurio/apicurio-registry-mem:latest`
