import { FunctionComponent, useEffect, useState } from "react";
import "./McpToolCompatibilityPanel.css";
import { Link } from "react-router";
import {
    Alert,
    EmptyState,
    EmptyStateBody,
    EmptyStateVariant,
    Label,
    List,
    ListItem,
    Spinner
} from "@patternfly/react-core";
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table";
import { McpToolCompatibilityResult, McpToolCompatibilityResults } from "@sdk/lib/generated-client/models";
import { McpToolService, useMcpToolService } from "@services/useMcpToolService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { Paging } from "@models/Paging.ts";
import {
    compatibilityNotices,
    compatibilitySummary,
    compatibilityViewState,
    verdictLabel
} from "./McpToolCompatibility.utils.ts";

const FIRST_PAGE: Paging = {
    page: 1,
    pageSize: 20
};

/**
 * Properties
 */
export type McpToolCompatibilityPanelProps = {
    groupId: string;
    artifactId: string;
    version: string;
};

/**
 * Lists the other registered MCP tools that can take the output of an MCP tool version as their
 * input, and those that cannot or might not, with the reasons and limitations of each comparison.
 */
export const McpToolCompatibilityPanel: FunctionComponent<McpToolCompatibilityPanelProps> = (
    { groupId, artifactId, version }: McpToolCompatibilityPanelProps) => {
    const [ isLoading, setLoading ] = useState<boolean>(true);
    const [ isError, setError ] = useState<boolean>(false);
    const [ results, setResults ] = useState<McpToolCompatibilityResults>();

    const mcpTools: McpToolService = useMcpToolService();
    const logger: LoggerService = useLoggerService();
    const appNavigation: AppNavigation = useAppNavigation();

    useEffect(() => {
        setLoading(true);
        setError(false);
        mcpTools.getToolCompatibility(groupId, artifactId, version, [], FIRST_PAGE).then(setResults).catch(error => {
            logger.error(error);
            setError(true);
        }).finally(() => setLoading(false));
    }, [groupId, artifactId, version]);

    const message = (title: string, body: string) => (
        <EmptyState titleText={ title } variant={ EmptyStateVariant.xs }>
            <EmptyStateBody>{ body }</EmptyStateBody>
        </EmptyState>
    );

    const toolLink = (result: McpToolCompatibilityResult) => {
        const tool = result.tool;
        const toolGroupId: string = tool?.groupId || "default";
        const toolArtifactId: string = tool?.artifactId || "";
        return (
            <>
                <Link to={ appNavigation.createLink(`/explore/${ encodeURIComponent(toolGroupId) }/${ encodeURIComponent(toolArtifactId) }`) }>
                    { tool?.title || tool?.name || toolArtifactId }
                </Link>
                <div className="mcp-tool-compatibility-id">{ toolGroupId } / { toolArtifactId }</div>
            </>
        );
    };

    const details = (result: McpToolCompatibilityResult) => (
        <List isPlain>
            { result.reasons?.map((reason, idx) => (
                <ListItem key={ `reason-${idx}` }>
                    { reason.message }
                    <div className="mcp-tool-compatibility-pointer">
                        <code>{ reason.producerPointer }</code> to <code>{ reason.consumerPointer }</code>
                    </div>
                </ListItem>
            )) }
            { result.limitations?.map((limitation, idx) => (
                <ListItem key={ `limitation-${idx}` }>
                    { limitation.message }
                    <div className="mcp-tool-compatibility-pointer"><code>{ limitation.pointer }</code></div>
                </ListItem>
            )) }
        </List>
    );

    const content = () => {
        switch (compatibilityViewState(isLoading, isError, results)) {
            case "loading":
                return <Spinner size="lg" aria-label="Loading compatibility information" />;
            case "error":
                return message("Error", "Compatibility information could not be loaded.");
            case "unavailable":
                return message("Unavailable", "Compatibility information is unavailable for this tool.");
            case "notComparable":
                return (
                    <EmptyState titleText="Not compared" variant={ EmptyStateVariant.xs }>
                        <EmptyStateBody>
                            <List isPlain>
                                { results?.limitations?.map((limitation, idx) => (
                                    <ListItem key={ idx }>{ limitation.message }</ListItem>
                                )) }
                            </List>
                        </EmptyStateBody>
                    </EmptyState>
                );
            case "empty":
                return message("None found", "No other MCP tools were compared with this tool.");
            default:
                return (
                    <>
                        <p className="mcp-tool-compatibility-summary">{ compatibilitySummary(results!) }</p>
                        { compatibilityNotices(results!).map(notice => (
                            <Alert key={ notice } variant="info" isInline isPlain title={ notice } />
                        )) }
                        <Table aria-label="Compatible tools" variant="compact">
                            <Thead>
                                <Tr>
                                    <Th>Tool</Th>
                                    <Th>Verdict</Th>
                                    <Th>Details</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                { results!.results!.map(result => {
                                    const label = verdictLabel(result.verdict);
                                    return (
                                        <Tr key={ `${result.tool?.groupId}/${result.tool?.artifactId}` }>
                                            <Td dataLabel="Tool">{ toolLink(result) }</Td>
                                            <Td dataLabel="Verdict"><Label color={ label.color }>{ label.text }</Label></Td>
                                            <Td dataLabel="Details">{ details(result) }</Td>
                                        </Tr>
                                    );
                                }) }
                            </Tbody>
                        </Table>
                    </>
                );
        }
    };

    return (
        <div className="mcp-tool-compatibility">
            { content() }
        </div>
    );
};
