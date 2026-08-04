import { FunctionComponent } from "react";
import { Alert, Label, Spinner, Title } from "@patternfly/react-core";

export type RenderValidationError = { path?: string; message: string };

export type PlaygroundRenderResultProps = {
    isRendering: boolean;
    serverRendered: string;
    localPreview: string;
    isModified: boolean;
    renderError: string;
    validationErrors: RenderValidationError[];
};

/**
 * Displays the server-rendered output, optional local preview,
 * and any validation/render errors. Extracted from PromptPlaygroundPage to follow SRP.
 */
export const PlaygroundRenderResult: FunctionComponent<PlaygroundRenderResultProps> = (props) => {
    const { isRendering, serverRendered, localPreview, isModified, renderError, validationErrors } = props;

    return (
        <>
            {isRendering && <Spinner size="md" />}

            {renderError && (
                <Alert variant="danger" title="Render Error" className="validation-errors">
                    {renderError}
                </Alert>
            )}

            {validationErrors.length > 0 && (
                <Alert variant="warning" title="Validation Errors" className="validation-errors">
                    <ul>
                        {validationErrors.map((ve) => (
                            <li key={`${ve.path ?? "root"}-${ve.message}`}>
                                {ve.path ? `${ve.path}: ` : ""}{ve.message}
                            </li>
                        ))}
                    </ul>
                </Alert>
            )}

            {/* Server-rendered output */}
            {serverRendered && (
                <div className="rendered-section">
                    <Title headingLevel="h3" size="md">Server-Rendered (validated)</Title>
                    <div className="render-source-label">
                        <Label color="green" isCompact>Server</Label>{" "}
                        Uses stored template with backend validation
                    </div>
                    <div className="rendered-output">{serverRendered}</div>
                </div>
            )}

            {/* Client-side preview (only when template is modified) */}
            {localPreview && isModified && (
                <div className="rendered-section">
                    <Title headingLevel="h3" size="md">Local Preview (unvalidated)</Title>
                    <div className="render-source-label">
                        <Label color="orange" isCompact>Local</Label>{" "}
                        Client-side substitution — no schema validation applied
                    </div>
                    <div className="rendered-output">{localPreview}</div>
                </div>
            )}
        </>
    );
};
