export const JSON_ARTIFACT_CONTENT: string = JSON.stringify({ answer: 42 }, null, 2);

export const PROTOBUF_ARTIFACT_CONTENT: string =
    "syntax = \"proto3\";\n\nmessage Answer {\n    int32 value = 1;\n}\n";
