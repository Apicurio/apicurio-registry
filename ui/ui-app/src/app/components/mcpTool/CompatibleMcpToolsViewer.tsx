import { FunctionComponent, useEffect, useState } from "react";
import "./CompatibleMcpToolsViewer.css";
import {
    Badge,
    Card,
    CardBody,
    CardHeader,
    CardTitle,
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm,
    Divider,
    EmptyState,
    EmptyStateBody,
    EmptyStateHeader,
    EmptyStateIcon,
    Label,
    LabelGroup,
    Spinner,
    Title
} from "@patternfly/react-core";
import { PlugIcon } from "@patternfly/react-icons";
import { McpToolSearchResult, useMcpToolsService } from "../../../services/useMcpToolsService";

export type CompatibleMcpToolsViewerProps = {
    groupId: string;
    artifactId: string;
    version?: string;
    className?: string;
};

/**
 * Component to display MCP tools compatible with a target tool's output schema (Output -> Input tool chaining).
 * Fetches real compatibility data from /.well-known/mcp-tools/{groupId}/{artifactId}/compatible.
 */
export const CompatibleMcpToolsViewer: FunctionComponent<CompatibleMcpToolsViewerProps> = (
    props: CompatibleMcpToolsViewerProps
) => {
    const { groupId, artifactId, version } = props;
    const mcpToolsService = useMcpToolsService();

    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const [tools, setTools] = useState<McpToolSearchResult[]>([]);

    useEffect(() => {
        let isMounted = true;
        setIsLoading(true);
        setError(null);

        mcpToolsService
            .getCompatibleMcpTools(groupId, artifactId, version)
            .then((results) => {
                if (isMounted) {
                    setTools(results.tools || []);
                    setIsLoading(false);
                }
            })
            .catch((err) => {
                if (isMounted) {
                    console.error("Failed to load compatible MCP tools:", err);
                    setError(err.message || "Failed to load compatible MCP tools");
                    setIsLoading(false);
                }
            });

        return () => {
            isMounted = false;
        };
    }, [groupId, artifactId, version]);

    return (
        <div className={`compatible-mcp-tools-viewer ${props.className || ""}`}>
            <Card isPlain>
                <CardHeader>
                    <CardTitle>
                        <Title headingLevel="h2">
                            Compatible Chained Tools <Badge isRead>{tools.length}</Badge>
                        </Title>
                    </CardTitle>
                </CardHeader>
                <CardBody>
                    <p className="compatible-mcp-tools-subtitle">
                        Tools in the registry whose required input parameters are guaranteed by this tool&apos;s output schema (Output &rarr; Input Chaining).
                    </p>
                </CardBody>
            </Card>

            <Divider className="mcp-tool-divider" />

            {isLoading && (
                <div className="compatible-mcp-tools-loading" data-testid="loading-state">
                    <Spinner size="lg" aria-label="Loading compatible tools" />
                    <span className="pf-v5-u-ml-sm">Checking tool compatibility...</span>
                </div>
            )}

            {!isLoading && error && (
                <EmptyState data-testid="error-state">
                    <EmptyStateHeader
                        titleText="Error Loading Compatible Tools"
                        headingLevel="h4"
                    />
                    <EmptyStateBody>{error}</EmptyStateBody>
                </EmptyState>
            )}

            {!isLoading && !error && tools.length === 0 && (
                <EmptyState data-testid="empty-state">
                    <EmptyStateHeader
                        titleText="No Compatible Tools Found"
                        icon={<EmptyStateIcon icon={PlugIcon} />}
                        headingLevel="h4"
                    />
                    <EmptyStateBody>
                        No registered MCP tools in the registry can accept this tool&apos;s guaranteed output as input.
                    </EmptyStateBody>
                </EmptyState>
            )}

            {!isLoading && !error && tools.length > 0 && (
                <div className="compatible-mcp-tools-list" data-testid="tools-list">
                    {tools.map((tool) => (
                        <Card key={`${tool.groupId}/${tool.artifactId}`} className="compatible-tool-card" isCompact>
                            <CardHeader>
                                <CardTitle>
                                    <Title headingLevel="h3">{tool.title || tool.name}</Title>
                                </CardTitle>
                                <Label color="green" icon={<PlugIcon />}>
                                    Output &rarr; Input Compatible
                                </Label>
                            </CardHeader>
                            <CardBody>
                                <DescriptionList isHorizontal>
                                    <DescriptionListGroup>
                                        <DescriptionListTerm>Artifact</DescriptionListTerm>
                                        <DescriptionListDescription>
                                            <code>{tool.groupId} / {tool.artifactId}</code>
                                        </DescriptionListDescription>
                                    </DescriptionListGroup>
                                    {tool.description && (
                                        <DescriptionListGroup>
                                            <DescriptionListTerm>Description</DescriptionListTerm>
                                            <DescriptionListDescription>{tool.description}</DescriptionListDescription>
                                        </DescriptionListGroup>
                                    )}
                                    {tool.parameters && tool.parameters.length > 0 && (
                                        <DescriptionListGroup>
                                            <DescriptionListTerm>Input Parameters</DescriptionListTerm>
                                            <DescriptionListDescription>
                                                <LabelGroup>
                                                    {tool.parameters.map((param) => (
                                                        <Label key={param} color="blue" isCompact>
                                                            {param}
                                                        </Label>
                                                    ))}
                                                </LabelGroup>
                                            </DescriptionListDescription>
                                        </DescriptionListGroup>
                                    )}
                                </DescriptionList>
                            </CardBody>
                        </Card>
                    ))}
                </div>
            )}
        </div>
    );
};
