import "@stoplight/mosaic/themes/default.css";
import "@stoplight/mosaic/styles.css";
import "./App.css";
import { FunctionComponent, useEffect, useState } from "react";
import { useWindow } from "@app/hooks/useWindow.ts";
import { JsonSchemaViewer } from "@stoplight/json-schema-viewer";
import { DEMO_JSON_SCHEMA } from "@app/DemoData.ts";
import { If } from "@app/components/common/If.tsx";

enum ContentType {
    JSON = "JSON", OTHER = "OTHER"
}

export type DocsUpdateType = {
    content: any;
    contentType: ContentType;
};

export const App: FunctionComponent<object> = () => {
    const [content, setContent] = useState<any>({});
    const [contentType, setContentType] = useState(ContentType.OTHER);

    const w: Window = useWindow();
    const queryParams = new URLSearchParams(w.location.search);

    useEffect(() => {
        const isDemo: boolean = queryParams.get("demo") === "true";
        if (isDemo) {
            setContent(DEMO_JSON_SCHEMA);
            setContentType(ContentType.JSON);
        } else {
            w.onmessage = (evt: MessageEvent) => {
                console.info("[App] OnMessage received: ", evt);
                if (evt.data && evt.data.type && evt.data.type === "apicurio-docs-render") {
                    const update: DocsUpdateType = evt.data.data as DocsUpdateType;
                    console.info("[App] Render docs message received from parent frame: ", update.contentType);
                    setContent(update.content);
                    setContentType(update.contentType);
                }
            };
        }
    }, []);

    return (
        <>
            <If condition={contentType === ContentType.JSON}>
                <div className="json-schema-container">
                    <JsonSchemaViewer schema={content} />
                </div>
            </If>
        </>
    );
};
