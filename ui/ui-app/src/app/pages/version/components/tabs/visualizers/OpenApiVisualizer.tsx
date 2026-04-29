import { FunctionComponent, useEffect, useRef } from "react";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";

export type OpenApiVisualizerProps = {
    spec: any;
    className?: string;
};


export const OpenApiVisualizer: FunctionComponent<OpenApiVisualizerProps> = (props: OpenApiVisualizerProps) => {
    const config: ConfigService = useConfigService();
    const logger: LoggerService = useLoggerService();
    const ref = useRef<HTMLIFrameElement>(null);
    const iframeLoaded = useRef<boolean>(false);

    const oaiDocsUrl = (): string => {
        let rval: string = config.uiOaiDocsUrl() || "/docs";
        if (rval.startsWith("/")) {
            rval = window.location.origin + rval;
        }
        return rval;
    };

    logger.info("[OpenApiVisualizer] OAI docs URL: ", oaiDocsUrl());

    const sendSpecToIframe = (spec: Record<string, unknown>): void => {
        if (ref.current?.contentWindow) {
            const message = {
                type: "apicurio-docs-render",
                data: {
                    contentType: "OPENAPI",
                    content: spec
                }
            };
            ref.current.contentWindow.postMessage(message, "*");
        }
    };

    const onIframeLoaded = (): void => {
        iframeLoaded.current = true;
        sendSpecToIframe(props.spec);
    };

    // Re-send the spec when it changes after the iframe has already loaded.
    // This handles the race condition where the parent fetches content asynchronously
    // and the iframe onLoad fires before the spec is available.
    useEffect(() => {
        if (iframeLoaded.current && props.spec && Object.keys(props.spec).length > 0) {
            sendSpecToIframe(props.spec);
        }
    }, [props.spec]);

    return (
        <iframe id="openapi-editor-frame"
            ref={ ref }
            style={{ width: "100%", height: "100%" }}
            className={ props.className ? props.className : "openapi-docs-container" }
            onLoad={ onIframeLoaded }
            src={ oaiDocsUrl() } />
    );

};
