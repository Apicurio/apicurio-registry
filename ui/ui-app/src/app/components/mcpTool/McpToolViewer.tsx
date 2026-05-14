import { FunctionComponent } from "react";
import "./McpToolViewer.css";
import {
    Card,
    CardBody,
    CardHeader,
    CardTitle,
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm,
    Divider,
    Label,
    LabelGroup,
    Title
} from "@patternfly/react-core";

/**
 * Properties for the MCP tool viewer.
 */
export type McpToolViewerProps = {
    spec: any;
    className?: string;
};

/**
 * Component to display an MCP tool definition in a structured, read-only view.
 * Follows the MCP specification 2025-11-25.
 */
export const McpToolViewer: FunctionComponent<McpToolViewerProps> = (props: McpToolViewerProps) => {
    const spec = props.spec || {};
    const inputSchema = spec.inputSchema || {};
    const outputSchema = spec.outputSchema;
    const properties = inputSchema.properties || {};
    const required: string[] = inputSchema.required || [];
    const annotations = spec.annotations || {};
    const paramNames = Object.keys(properties);
    const hasAnnotations = annotations.title || annotations.audience || annotations.priority !== undefined;
    // MCP spec display name fallback: title → annotations.title → name
    const displayTitle = spec.title || annotations.title || spec.name || "Unnamed Tool";

    return (
        <div className={`mcp-tool-viewer ${props.className || ""}`}>
            <Card isPlain>
                <CardHeader>
                    <CardTitle>
                        <Title headingLevel="h2">{displayTitle}</Title>
                    </CardTitle>
                </CardHeader>
                <CardBody>
                    <DescriptionList isHorizontal>
                        {spec.name && (
                            <DescriptionListGroup>
                                <DescriptionListTerm>Name</DescriptionListTerm>
                                <DescriptionListDescription><code>{spec.name}</code></DescriptionListDescription>
                            </DescriptionListGroup>
                        )}
                        {spec.title && (
                            <DescriptionListGroup>
                                <DescriptionListTerm>Title</DescriptionListTerm>
                                <DescriptionListDescription>{spec.title}</DescriptionListDescription>
                            </DescriptionListGroup>
                        )}
                        {spec.description && (
                            <DescriptionListGroup>
                                <DescriptionListTerm>Description</DescriptionListTerm>
                                <DescriptionListDescription>{spec.description}</DescriptionListDescription>
                            </DescriptionListGroup>
                        )}
                    </DescriptionList>
                </CardBody>
            </Card>

            {/* Annotations */}
            {hasAnnotations && (
                <>
                    <Divider className="mcp-tool-divider" />
                    <Card isPlain>
                        <CardHeader>
                            <CardTitle>
                                <Title headingLevel="h3">Annotations</Title>
                            </CardTitle>
                        </CardHeader>
                        <CardBody>
                            <LabelGroup>
                                {annotations.title && (
                                    <Label color="grey">Title: {annotations.title}</Label>
                                )}
                                {annotations.audience && Array.isArray(annotations.audience) && (
                                    annotations.audience.map((role: string) => (
                                        <Label key={role} color="blue">Audience: {role}</Label>
                                    ))
                                )}
                                {annotations.priority !== undefined && (
                                    <Label color="green">Priority: {annotations.priority}</Label>
                                )}
                            </LabelGroup>
                        </CardBody>
                    </Card>
                </>
            )}

            {/* Input Schema */}
            {paramNames.length > 0 && (
                <>
                    <Divider className="mcp-tool-divider" />
                    <Card isPlain>
                        <CardHeader>
                            <CardTitle>
                                <Title headingLevel="h3">Input Parameters</Title>
                            </CardTitle>
                        </CardHeader>
                        <CardBody>
                            <table className="pf-v5-c-table pf-m-compact pf-m-grid-md">
                                <thead>
                                    <tr>
                                        <th>Parameter</th>
                                        <th>Type</th>
                                        <th>Required</th>
                                        <th>Description</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {paramNames.map((paramName) => {
                                        const param = properties[paramName] || {};
                                        const isRequired = required.includes(paramName);
                                        return (
                                            <tr key={paramName}>
                                                <td><strong>{paramName}</strong></td>
                                                <td>{param.type || "any"}</td>
                                                <td>
                                                    {isRequired ? (
                                                        <Label color="red" isCompact>required</Label>
                                                    ) : (
                                                        <Label color="grey" isCompact>optional</Label>
                                                    )}
                                                </td>
                                                <td>{param.description || "-"}</td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </CardBody>
                    </Card>
                </>
            )}

            {/* Output Schema */}
            {outputSchema && outputSchema.properties && (
                <>
                    <Divider className="mcp-tool-divider" />
                    <Card isPlain>
                        <CardHeader>
                            <CardTitle>
                                <Title headingLevel="h3">Output Schema</Title>
                            </CardTitle>
                        </CardHeader>
                        <CardBody>
                            <table className="pf-v5-c-table pf-m-compact pf-m-grid-md">
                                <thead>
                                    <tr>
                                        <th>Field</th>
                                        <th>Type</th>
                                        <th>Required</th>
                                        <th>Description</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {Object.keys(outputSchema.properties).map((fieldName) => {
                                        const field = outputSchema.properties[fieldName] || {};
                                        const isRequired = (outputSchema.required || []).includes(fieldName);
                                        return (
                                            <tr key={fieldName}>
                                                <td><strong>{fieldName}</strong></td>
                                                <td>{field.type || "any"}</td>
                                                <td>
                                                    {isRequired ? (
                                                        <Label color="red" isCompact>required</Label>
                                                    ) : (
                                                        <Label color="grey" isCompact>optional</Label>
                                                    )}
                                                </td>
                                                <td>{field.description || "-"}</td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </CardBody>
                    </Card>
                </>
            )}
        </div>
    );
};
