import apicurioConfig from "@apicurio/eslint-config";

export default [
    {
        ignores: [ "dist/**", "lib/generated-client/**", ".kiota/**", "eslint.config.js" ]
    },
    ...apicurioConfig
];
