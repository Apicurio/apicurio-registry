import { FunctionComponent, useRef } from "react";
import { Services } from "@services/services.ts";
import { ConfigService } from "@services/config";

export type OpenApiVisualizerProps = {
    spec: any;
    className?: string;
};


export const OpenApiVisualizer: FunctionComponent<OpenApiVisualizerProps> = (props: OpenApiVisualizerProps) => {
    const config: ConfigService = Services.getConfigService();
    const ref = useRef<any>();

    const oaiDocsUrl = (): string => {
        let rval: string = config.uiOaiDocsUrl() || "/docs";
        if (rval.startsWith("/")) {
            rval = window.location.origin + rval;
        }
        return rval;
    };

    Services.getLoggerService().info("[OpenApiVisualizer] OAI docs URL: ", oaiDocsUrl());

    const onIframeLoaded = (): void => {
        // Now it's OK to post a message to iframe with the content to render.

        const message: any = {
            type: "apicurio-docs-render",
            // tslint:disable-next-line:object-literal-sort-keys
            data: {
                contentType: "OPENAPI",
                content: props.spec
            }
        };
        ref.current.contentWindow.postMessage(message, "*");
    };

    return (
        <iframe id="openapi-editor-frame"
            ref={ ref }
            style={{ width: "100%", height: "100%" }}
            className={ props.className ? props.className : "openapi-docs-container" }
            onLoad={ onIframeLoaded }
            src={ oaiDocsUrl() } />
    );

};
