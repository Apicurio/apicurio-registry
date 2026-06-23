import { ContentTypes } from "@models/ContentTypes.ts";
import { Template } from "@models/templates";
import THRIFT_BLANK from "./thrift/thrift-blank.json";

export const THRIFT_TEMPLATES: Template[] = [
    {
        id: "thrift_blank",
        name: "Blank Thrift IDL",
        description: "An empty/example schema using the Apache Thrift Interface Definition Language.",
        contentType: ContentTypes.APPLICATION_THRIFT,
        content: THRIFT_BLANK.template
    }
];
