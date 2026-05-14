import React, { FunctionComponent } from "react";
import { Link } from "react-router";
import "./PromptTemplateViewer.css";
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
    Flex,
    FlexItem,
    Label,
    LabelGroup,
    Title
} from "@patternfly/react-core";
import { JsonSchemaProperties } from "@app/components/jsonSchema/JsonSchemaProperties";

export interface PromptVariable {
    name?: string;
    type?: string;
    description?: string;
    required?: boolean;
    default?: any;
    enum?: string[];
    constraints?: any;
}

export interface PromptTemplateMetadata {
    author?: string;
    createdAt?: string;
    tags?: string[];
    recommendedModels?: string[];
    useCases?: string[];
    estimatedTokens?: {
        input?: number;
        variableOverhead?: number;
    };
}

export interface PromptTemplate {
    templateId?: string;
    name?: string;
    description?: string;
    version?: string;
    template?: string;
    variables?: Record<string, PromptVariable> | PromptVariable[];
    outputSchema?: any;
    metadata?: PromptTemplateMetadata;
    mcp?: {
        enabled?: boolean;
        name?: string;
        description?: string;
        arguments?: any[];
    };
}

export type PromptTemplateViewerProps = {
    promptTemplate: PromptTemplate;
    className?: string;
};

const highlightVariables = (template: string): React.ReactNode[] => {
    const parts: React.ReactNode[] = [];
    // Match both {{variable}} and {{#if variable}} / {{/if}} handlebars syntax
    const regex = /\{\{(#?\/?(?:if|unless|each|with)\s+)?(\w+)\}\}/g;
    let lastIndex = 0;
    let match;
    let key = 0;

    while ((match = regex.exec(template)) !== null) {
        if (match.index > lastIndex) {
            parts.push(template.substring(lastIndex, match.index));
        }
        const isBlock = !!match[1];
        parts.push(
            <span key={key++} className={isBlock ? "template-block" : "template-variable"}>
                {match[0]}
            </span>
        );
        lastIndex = match.index + match[0].length;
    }
    if (lastIndex < template.length) {
        parts.push(template.substring(lastIndex));
    }
    return parts;
};

const getVariablesList = (variables: Record<string, PromptVariable> | PromptVariable[] | undefined): { name: string; variable: PromptVariable }[] => {
    if (!variables) return [];
    if (Array.isArray(variables)) {
        return variables.map(v => ({ name: v.name || "", variable: v }));
    }
    return Object.entries(variables).map(([name, variable]) => ({ name, variable }));
};

export const PromptTemplateViewer: FunctionComponent<PromptTemplateViewerProps> = (props: PromptTemplateViewerProps) => {
    const { promptTemplate, className } = props;
    const variablesList = getVariablesList(promptTemplate.variables);
    const meta = promptTemplate.metadata;

    return (
        <Card className={`prompt-template-viewer ${className || ""}`}>
            <CardHeader>
                <CardTitle>
                    <Flex>
                        <FlexItem>
                            <Title headingLevel="h2" className="template-name">
                                {promptTemplate.name || promptTemplate.templateId || "Unnamed Template"}
                            </Title>
                        </FlexItem>
                        {promptTemplate.version && (
                            <FlexItem align={{ default: "alignRight" }}>
                                <Label color="blue" isCompact>
                                    v{promptTemplate.version}
                                </Label>
                            </FlexItem>
                        )}
                    </Flex>
                    {promptTemplate.description && (
                        <p className="template-description">{promptTemplate.description}</p>
                    )}
                </CardTitle>
            </CardHeader>
            <CardBody>
                <DescriptionList isCompact className="template-basic-info">
                    {promptTemplate.templateId && (
                        <DescriptionListGroup>
                            <DescriptionListTerm>Template ID</DescriptionListTerm>
                            <DescriptionListDescription>
                                <code>{promptTemplate.templateId}</code>
                            </DescriptionListDescription>
                        </DescriptionListGroup>
                    )}
                    {meta?.author && (
                        <DescriptionListGroup>
                            <DescriptionListTerm>Author</DescriptionListTerm>
                            <DescriptionListDescription>{meta.author}</DescriptionListDescription>
                        </DescriptionListGroup>
                    )}
                    {meta?.createdAt && (
                        <DescriptionListGroup>
                            <DescriptionListTerm>Created</DescriptionListTerm>
                            <DescriptionListDescription>{meta.createdAt}</DescriptionListDescription>
                        </DescriptionListGroup>
                    )}
                </DescriptionList>

                {promptTemplate.template && (
                    <>
                        <Divider className="section-divider" />
                        <Title headingLevel="h3" size="md">Template</Title>
                        <div className="template-preview">
                            {highlightVariables(promptTemplate.template)}
                        </div>
                    </>
                )}

                {variablesList.length > 0 && (
                    <>
                        <Divider className="section-divider" />
                        <Title headingLevel="h3" size="md">Variables</Title>
                        <div className="variables-table-wrapper">
                            <table className="variables-table">
                                <thead>
                                    <tr>
                                        <th>Name</th>
                                        <th>Type</th>
                                        <th>Required</th>
                                        <th>Default</th>
                                        <th>Description</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {variablesList.map(({ name, variable }, index) => (
                                        <tr key={index}>
                                            <td><code>{name}</code></td>
                                            <td>
                                                <Label color="blue" isCompact>{variable.type || "string"}</Label>
                                            </td>
                                            <td>
                                                {variable.required ? (
                                                    <Label color="orange" isCompact>required</Label>
                                                ) : (
                                                    <Label color="grey" isCompact>optional</Label>
                                                )}
                                            </td>
                                            <td>{variable.default !== undefined ? (
                                                <code>{String(variable.default)}</code>
                                            ) : "-"}</td>
                                            <td>{variable.description || "-"}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </>
                )}

                {meta?.tags && meta.tags.length > 0 && (
                    <>
                        <Divider className="section-divider" />
                        <Title headingLevel="h3" size="md">Tags</Title>
                        <LabelGroup className="section-content">
                            {meta.tags.map((tag, index) => (
                                <Label key={index} color="grey" isCompact>{tag}</Label>
                            ))}
                        </LabelGroup>
                    </>
                )}

                {meta?.recommendedModels && meta.recommendedModels.length > 0 && (
                    <>
                        <Divider className="section-divider" />
                        <Title headingLevel="h3" size="md">Recommended Models</Title>
                        <LabelGroup className="section-content">
                            {meta.recommendedModels.map((model, index) => (
                                <Label key={index} color="purple" isCompact>
                                    <Link to={`/explore/default/${encodeURIComponent(model)}`} className="model-link">
                                        {model}
                                    </Link>
                                </Label>
                            ))}
                        </LabelGroup>
                    </>
                )}

                {meta?.useCases && meta.useCases.length > 0 && (
                    <>
                        <Divider className="section-divider" />
                        <Title headingLevel="h3" size="md">Use Cases</Title>
                        <LabelGroup className="section-content">
                            {meta.useCases.map((uc, index) => (
                                <Label key={index} color="teal" isCompact>{uc}</Label>
                            ))}
                        </LabelGroup>
                    </>
                )}

                {promptTemplate.mcp?.enabled && (
                    <>
                        <Divider className="section-divider" />
                        <Title headingLevel="h3" size="md">MCP Integration</Title>
                        <DescriptionList isCompact className="section-content">
                            {promptTemplate.mcp.name && (
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Tool Name</DescriptionListTerm>
                                    <DescriptionListDescription>
                                        <code>{promptTemplate.mcp.name}</code>
                                    </DescriptionListDescription>
                                </DescriptionListGroup>
                            )}
                            {promptTemplate.mcp.description && (
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Description</DescriptionListTerm>
                                    <DescriptionListDescription>{promptTemplate.mcp.description}</DescriptionListDescription>
                                </DescriptionListGroup>
                            )}
                            {promptTemplate.mcp.arguments && promptTemplate.mcp.arguments.length > 0 && (
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Arguments</DescriptionListTerm>
                                    <DescriptionListDescription>
                                        <LabelGroup>
                                            {promptTemplate.mcp.arguments.map((arg: any, i: number) => (
                                                <Label key={i} color={arg.required ? "orange" : "grey"} isCompact>
                                                    {arg.name}
                                                </Label>
                                            ))}
                                        </LabelGroup>
                                    </DescriptionListDescription>
                                </DescriptionListGroup>
                            )}
                        </DescriptionList>
                    </>
                )}

                {promptTemplate.outputSchema && (
                    <>
                        <Divider className="section-divider" />
                        <Title headingLevel="h3" size="md">Output Schema</Title>
                        <div className="section-content">
                            <JsonSchemaProperties schema={promptTemplate.outputSchema} depth={0} />
                        </div>
                    </>
                )}
            </CardBody>
        </Card>
    );
};
