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
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table";

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
                            <Table aria-label="Input parameters" variant="compact">
                                <Thead>
                                    <Tr>
                                        <Th>Parameter</Th>
                                        <Th>Type</Th>
                                        <Th>Required</Th>
                                        <Th>Default</Th>
                                        <Th>Allowed Values</Th>
                                        <Th>Description</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {paramNames.map((paramName) => {
                                        const param = properties[paramName] || {};
                                        const isRequired = required.includes(paramName);
                                        return (
                                            <Tr key={paramName}>
                                                <Td dataLabel="Parameter"><strong>{paramName}</strong></Td>
                                                <Td dataLabel="Type">{param.type || "any"}</Td>
                                                <Td dataLabel="Required">
                                                    {isRequired ? (
                                                        <Label color="red" isCompact>required</Label>
                                                    ) : (
                                                        <Label color="grey" isCompact>optional</Label>
                                                    )}
                                                </Td>
                                                <Td dataLabel="Default">{param.default !== undefined ? (
                                                        <code>{String(param.default)}</code>
                                                    ) : "-"}</Td>
                                                <Td dataLabel="Allowed Values">
                                                    {param.enum && Array.isArray(param.enum) && param.enum.length > 0 ? (
                                                        <LabelGroup>
                                                            {param.enum.map((val: any, i: number) => (
                                                                <Label key={i} color="grey" isCompact>{String(val)}</Label>
                                                            ))}
                                                        </LabelGroup>
                                                    ) : "-"}
                                                </Td>
                                                <Td dataLabel="Description">{param.description || "-"}</Td>
                                            </Tr>
                                        );
                                    })}
                                </Tbody>
                            </Table>
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
                            <Table aria-label="Output schema" variant="compact">
                                <Thead>
                                    <Tr>
                                        <Th>Field</Th>
                                        <Th>Type</Th>
                                        <Th>Required</Th>
                                        <Th>Default</Th>
                                        <Th>Allowed Values</Th>
                                        <Th>Description</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {Object.keys(outputSchema.properties).map((fieldName) => {
                                        const field = outputSchema.properties[fieldName] || {};
                                        const isRequired = (outputSchema.required || []).includes(fieldName);
                                        return (
                                            <Tr key={fieldName}>
                                                <Td dataLabel="Field"><strong>{fieldName}</strong></Td>
                                                <Td dataLabel="Type">{field.type || "any"}</Td>
                                                <Td dataLabel="Required">
                                                    {isRequired ? (
                                                        <Label color="red" isCompact>required</Label>
                                                    ) : (
                                                        <Label color="grey" isCompact>optional</Label>
                                                    )}
                                                </Td>
                                                <Td dataLabel="Default">{field.default !== undefined ? (
                                                        <code>{String(field.default)}</code>
                                                    ) : "-"}</Td>
                                                <Td dataLabel="Allowed Values">
                                                    {field.enum && Array.isArray(field.enum) && field.enum.length > 0 ? (
                                                        <LabelGroup>
                                                            {field.enum.map((val: any, i: number) => (
                                                                <Label key={i} color="grey" isCompact>{String(val)}</Label>
                                                            ))}
                                                        </LabelGroup>
                                                    ) : "-"}
                                                </Td>
                                                <Td dataLabel="Description">{field.description || "-"}</Td>
                                            </Tr>
                                        );
                                    })}
                                </Tbody>
                            </Table>
                        </CardBody>
                    </Card>
                </>
            )}
        </div>
    );
};
