import apicurioConfig from "@apicurio/eslint-config";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";

export default [
    {
        ignores: ["dist/**", "eslint.config.js", "public/**", ".fix_yaml.cjs", "configs/**", "config.js", "version.js"]
    },
    ...apicurioConfig,
    {
        files: ["**/*.ts", "**/*.tsx"],
        plugins: {
            "react-hooks": reactHooks,
            "react-refresh": reactRefresh
        },
        rules: {
            "react-hooks/rules-of-hooks": "error",
            "react-hooks/exhaustive-deps": "off",
            "react-refresh/only-export-components": [
                "warn",
                { allowConstantExport: true }
            ]
        }
    }
];
