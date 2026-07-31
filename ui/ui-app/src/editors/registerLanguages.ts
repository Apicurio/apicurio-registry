import { Monaco } from "@monaco-editor/react";
import { registerProto } from "./ProtobufLanguage.ts";
import { registerGraphQL } from "./GraphQLLanguage.ts";

export const registerCustomLanguages = (monaco: Monaco) => {
    registerProto(monaco);
    registerGraphQL(monaco);
};
