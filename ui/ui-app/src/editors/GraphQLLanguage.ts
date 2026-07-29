import { Monaco } from "@monaco-editor/react";


let graphqlRegistered = false;

export const registerGraphQL = (monaco: Monaco) => {
    if (graphqlRegistered) return;
    graphqlRegistered = true;

    monaco.languages.register({ id: "graphql" });
    monaco.languages.setMonarchTokensProvider("graphql", {
        keywords: [
            "type", "query", "mutation", "subscription", "interface",
            "union", "enum", "scalar", "implements", "directive", "on", "extend"
        ],
        typeKeywords: [
            "Int", "Float", "String", "Boolean", "ID"
        ],
        operators: [
            "=", "!", "?", "&", "|"
        ],
        symbols: /[=!?:&|]+/,
        escapes: /\\(?:["\\/bfnrt]|u[0-9A-Fa-f]{4})/,
        tokenizer: {
            root: [
                [/[a-z_$][\w$]*/, {
                    cases: {
                        "@typeKeywords": "type.identifier",
                        "@keywords": "keyword",
                        "@default": "identifier"
                    }
                }],
                [/[A-Z][\w$]*/, "type.identifier"],
                { include: "@whitespace" },

                [/[{}()[\]]/, "@brackets"],
                [/@symbols/, {
                    cases: {
                        "@operators": "operator",
                        "@default": ""
                    }
                }],

                [/@\s*[a-zA-Z_$][\w$]*/, { token: "annotation" }],
                [/\d*\.\d+([eE][-+]?\d+)?/, "number.float"],
                [/\d+/, "number"],
                [/[;,.]/, "delimiter"],

                [/"([^"\\]|\\.)*$/, "string.invalid"],
                [/"/, { token: "string.quote", bracket: "@open", next: "@string" }]
            ],
            comment: [
                [/[^#]+/, "comment"],
                [/#.*$/, "comment"]
            ],
            string: [
                [/[^\\"]+/, "string"],
                [/@escapes/, "string.escape"],
                [/\\./, "string.escape.invalid"],
                [/"/, { token: "string.quote", bracket: "@close", next: "@pop" }]
            ],
            whitespace: [
                [/[ \t\r\n]+/, "white"],
                [/#.*$/, "comment"]
            ]
        }
    });
};
