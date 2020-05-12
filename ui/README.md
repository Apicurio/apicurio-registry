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

Start the development server
`yarn start:registry`

## Configurations
* [TypeScript Config](./tsconfig.json)
* [Webpack Config](./webpack.common.js)

## Code Quality Tools
* To keep our bundle size in check, we use [webpack-bundle-analyzer](https://github.com/webpack-contrib/webpack-bundle-analyzer)
* To keep our code formatting in check, we use [prettier](https://github.com/prettier/prettier)
