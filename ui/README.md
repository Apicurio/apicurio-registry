# Apicurio Registry React PoC

Apicurio Registry React based Single Page Application based on Patternfly 4

## Requirements
This project requires node version 10.x.x and npm version 6.x.x.  It also uses the yarn package manager (version 1.13.0 or higher). 
Prior to building this project make sure you have these applications installed.  After installing node and npm you 
can install yarn globally by typing:

`npm install yarn -g'

## Development Scripts

Install development/build dependencies
`yarn`

Start the development server
`yarn start:registry`

Run a full build
`yarn build`

Run the test suite
`yarn test`

Run the linter
`yarn lint`

Launch a tool to inspect the bundle size
`yarn bundle-profile:analyze`

## Configurations
* [TypeScript Config](./tsconfig.json)
* [Webpack Config](./webpack.common.js)
* [Jest Config](./jest.config.js)

## Code Quality Tools
* For accessibility compliance, we use [react-axe](https://github.com/dequelabs/react-axe)
* To keep our bundle size in check, we use [webpack-bundle-analyzer](https://github.com/webpack-contrib/webpack-bundle-analyzer)
* To keep our code formatting in check, we use [prettier](https://github.com/prettier/prettier)
* To keep our code logic and test coverage in check, we use [jest](https://github.com/facebook/jest)
