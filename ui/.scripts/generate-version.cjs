#! /usr/bin/env node
const fs = require("fs");
const packageJson = require('../package.json');


console.info("-------------------------------------------------------");
console.info("Generating version.js");
console.info("-------------------------------------------------------");


const VERSION_OUTPUT_PATH="./ui-app/dist/version.js";

// Generate the version.js file.
const info = {
    name: "Apicurio Registry",
    version: packageJson.version,
    builtOn: new Date(),
    url: "http://www.apicur.io/"
};

const FILE_CONTENT = `
const ApicurioInfo = ${JSON.stringify(info, null, 4)};
`;
console.info(FILE_CONTENT);
console.info("-------------------------------------------------------");

fs.writeFile(VERSION_OUTPUT_PATH, FILE_CONTENT, "utf8", (err) => {
    if (err) {
      console.error("Error writing config to file:", err);
      return;
    }
    console.log("Config successfully writen to file.");
});
