import { FunctionComponent, useRef } from "react";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";

export type JsonSchemaVisualizerProps = {
    spec: any;
    className?: string;
};


export const JsonSchemaVisualizer: FunctionComponent<JsonSchemaVisualizerProps> = (props: JsonSchemaVisualizerProps) => {
    const config: ConfigService = useConfigService();
    const logger: LoggerService = useLoggerService();
    const ref = useRef<any>(null);

    const jsonSchemaDocsUrl = (): string => {
        let rval: string = config.uiJsonSchemaDocsUrl() || "/docs-json";
        if (rval.startsWith("/")) {
            rval = window.location.origin + rval;
        }
        return rval;
    };

    logger.info("[JsonSchemaVisualizer] JSON Schema docs URL: ", jsonSchemaDocsUrl());

    const onIframeLoaded = (): void => {
        // Now it's OK to post a message to iframe with the content to render.

        const message: any = {
            type: "apicurio-docs-render",
            // tslint:disable-next-line:object-literal-sort-keys
            data: {
                contentType: "JSON",
                content: props.spec
            }
        };
        ref.current.contentWindow.postMessage(message, "*");
    };

    return (
        <iframe id="json-schema-docs-frame"
            ref={ ref }
            style={{ width: "100%", height: "100%" }}
            className={ props.className ? props.className : "json-schema-docs-container" }
            onLoad={ onIframeLoaded }
            src={ jsonSchemaDocsUrl() } />
    );

};
