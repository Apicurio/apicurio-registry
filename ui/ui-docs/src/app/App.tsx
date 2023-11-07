import "./App.css";
import { FunctionComponent, useEffect, useState } from "react";
import { useWindow } from "@app/hooks/useWindow.ts";
import { RedocStandalone } from "redoc";
import { DEMO_OPENAPI } from "@app/DemoData.ts";
import { If } from "@app/components/common/If.tsx";

enum ContentType {
    OPENAPI = "OPENAPI", OTHER = "OTHER"
}

export type DocsUpdateType = {
    content: any;
    contentType: ContentType;
};

export type AppProps = {
    // No props
};

export const App: FunctionComponent<AppProps> = () => {
    const [content, setContent] = useState<any>({});
    const [contentType, setContentType] = useState(ContentType.OTHER);

    const w: Window = useWindow();
    const queryParams = new URLSearchParams(w.location.search);

    useEffect(() => {
        const isDemo: boolean = queryParams.get("demo") === "true";
        if (isDemo) {
            setContent(DEMO_OPENAPI);
            setContentType(ContentType.OPENAPI);
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
            <If condition={contentType === ContentType.OPENAPI}>
                <RedocStandalone spec={content} />
            </If>
        </>
    );
};
