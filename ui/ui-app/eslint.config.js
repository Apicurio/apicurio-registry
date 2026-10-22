import apicurioConfig from "@apicurio/eslint-config";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";

// Runtime (non-type-only) imports of Monaco and PatternFly's Monaco-backed CodeEditor must go
// through the shared lazy-loading gate in "src/app/components/codeEditor/RegistryEditors.tsx",
// so that Monaco is never pulled into the initial application bundle.  Only the two heavy
// implementation modules themselves (and their own tests) are allowed to import them directly.
const RESTRICTED_MONACO_IMPORTS = [
    {
        name: "monaco-editor",
        message: "Import Monaco through RegistryCodeEditor/RegistryDiffEditor (\"@app/components/codeEditor/RegistryEditors.tsx\"), or use \"import type\" for types only.",
        allowTypeImports: true
    },
    {
        name: "@monaco-editor/react",
        message: "Import Monaco through RegistryCodeEditor/RegistryDiffEditor (\"@app/components/codeEditor/RegistryEditors.tsx\"), or use \"import type\" for types only.",
        allowTypeImports: true
    },
    {
        name: "@monaco-editor/loader",
        message: "Configure the Monaco loader only from \"src/app/components/codeEditor/monacoRuntime.ts\".",
        allowTypeImports: true
    },
    {
        name: "@patternfly/react-code-editor",
        message: "Import RegistryPatternFlyCodeEditor from \"@app/components/codeEditor/RegistryEditors.tsx\" instead, or use \"import type\" for types only.",
        allowTypeImports: true
    }
];

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
    },
    {
        files: ["**/*.ts", "**/*.tsx"],
        ignores: [
            "src/app/components/codeEditor/monacoRuntime.ts",
            "src/app/components/codeEditor/monacoRuntime.test.ts",
            "src/app/components/codeEditor/PatternFlyEditorAdapter.tsx"
        ],
        plugins: {
            "@typescript-eslint": tseslint.plugin
        },
        rules: {
            "@typescript-eslint/no-restricted-imports": ["error", {
                paths: RESTRICTED_MONACO_IMPORTS
            }]
        }
    }
];
