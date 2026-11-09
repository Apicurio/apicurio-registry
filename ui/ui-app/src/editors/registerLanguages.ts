import type { Monaco } from "@monaco-editor/react";
import { registerProto } from "./ProtobufLanguage.ts";
import { registerGraphQL } from "./GraphQLLanguage.ts";
import { registerPromptTemplate } from "./PromptTemplateLanguage.ts";

export const registerCustomLanguages = (monaco: Monaco) => {
    registerProto(monaco);
    registerGraphQL(monaco);
    registerPromptTemplate(monaco);
};
