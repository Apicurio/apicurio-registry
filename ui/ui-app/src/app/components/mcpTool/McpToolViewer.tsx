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
 */
export const McpToolViewer: FunctionComponent<McpToolViewerProps> = (props: McpToolViewerProps) => {
    const spec = props.spec || {};
    const inputSchema = spec.inputSchema || {};
    const properties = inputSchema.properties || {};
    const required: string[] = inputSchema.required || [];
    const annotations = spec.annotations || {};
    const paramNames = Object.keys(properties);

    return (
        <div className={`mcp-tool-viewer ${props.className || ""}`}>
            <Card isPlain>
                <CardHeader>
                    <CardTitle>
                        <Title headingLevel="h2">{spec.name || "Unnamed Tool"}</Title>
                    </CardTitle>
                </CardHeader>
                <CardBody>
                    <DescriptionList isHorizontal>
                        {spec.description && (
                            <DescriptionListGroup>
                                <DescriptionListTerm>Description</DescriptionListTerm>
                                <DescriptionListDescription>{spec.description}</DescriptionListDescription>
                            </DescriptionListGroup>
                        )}
                        {spec.version && (
                            <DescriptionListGroup>
                                <DescriptionListTerm>Version</DescriptionListTerm>
                                <DescriptionListDescription>{spec.version}</DescriptionListDescription>
                            </DescriptionListGroup>
                        )}
                    </DescriptionList>
                </CardBody>
            </Card>

            {/* Annotations */}
            {Object.keys(annotations).length > 0 && (
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
                                {annotations.category && (
                                    <Label color="blue">Category: {annotations.category}</Label>
                                )}
                                {annotations.provider && (
                                    <Label color="green">Provider: {annotations.provider}</Label>
                                )}
                                {annotations.requiresAuth !== undefined && (
                                    <Label color={annotations.requiresAuth ? "orange" : "grey"}>
                                        {annotations.requiresAuth ? "Auth Required" : "No Auth"}
                                    </Label>
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
        </div>
    );
};
