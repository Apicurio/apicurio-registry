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
    Pagination,
    Spinner,
    ToggleGroup,
    ToggleGroupItem,
    Toolbar,
    ToolbarContent,
    ToolbarItem
} from "@patternfly/react-core";
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table";
import { ListWithToolbar } from "@apitomy/common-ui-components";
import {
    McpToolCompatibilityResult,
    McpToolCompatibilityResults,
    McpToolCompatibilityVerdict
} from "@sdk/lib/generated-client/models";
import { McpToolService, useMcpToolService } from "@services/useMcpToolService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { Paging } from "@models/Paging.ts";
import { newLoaderGuard } from "@utils/loader.utils.ts";
import {
    compatibilityNotices,
    compatibilitySummary,
    compatibilityViewState,
    verdictFilterLabel,
    verdictLabel
} from "./McpToolCompatibility.utils.ts";

const VERDICTS: McpToolCompatibilityVerdict[] = ["COMPATIBLE", "INCOMPATIBLE", "INDETERMINATE"];

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
    const [ verdict, setVerdict ] = useState<McpToolCompatibilityVerdict>("COMPATIBLE");
    const [ paging, setPaging ] = useState<Paging>(FIRST_PAGE);

    // Another tool version starts again from its compatible tools, without showing the previous results
    const source: string = `${groupId}/${artifactId}/${version}`;
    const [ shownSource, setShownSource ] = useState<string>(source);
    if (source !== shownSource) {
        setShownSource(source);
        setResults(undefined);
        setVerdict("COMPATIBLE");
        setPaging(FIRST_PAGE);
    }

    const mcpTools: McpToolService = useMcpToolService();
    const logger: LoggerService = useLoggerService();
    const appNavigation: AppNavigation = useAppNavigation();

    useEffect(() => {
        // A response that arrives after another request has started is not shown
        const guard = newLoaderGuard();
        setLoading(true);
        setError(false);
        mcpTools.getToolCompatibility(groupId, artifactId, version, [verdict], paging).then(guard.wrap(response => {
            setResults(response);
            setLoading(false);
        })).catch(guard.wrap(error => {
            logger.error(error);
            setError(true);
            setLoading(false);
        }));
        return () => guard.cancel();
    }, [groupId, artifactId, version, verdict, paging]);

    const onVerdictChange = (selected: McpToolCompatibilityVerdict): void => {
        if (selected === verdict) {
            return;
        }
        setVerdict(selected);
        setPaging({ ...paging, page: 1 });
    };

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

    const toolbar = (
        <Toolbar>
            <ToolbarContent>
                <ToolbarItem>
                    <ToggleGroup aria-label="Verdict filter">
                        { VERDICTS.map(item => (
                            <ToggleGroupItem
                                key={ item }
                                text={ verdictFilterLabel(item, results?.totals) }
                                buttonId={ `mcp-tool-compatibility-${item.toLowerCase()}` }
                                isSelected={ verdict === item }
                                onChange={ () => onVerdictChange(item) }
                            />
                        )) }
                    </ToggleGroup>
                </ToolbarItem>
                <ToolbarItem align={{ default: "alignEnd" }}>
                    <Pagination
                        variant="top"
                        dropDirection="down"
                        itemCount={ results?.count ?? 0 }
                        perPage={ paging.pageSize }
                        page={ paging.page }
                        onSetPage={ (_event, page) => setPaging({ ...paging, page }) }
                        onPerPageSelect={ (_event, pageSize) => setPaging({ page: 1, pageSize }) }
                        widgetId="mcp-tool-compatibility-pagination"
                    />
                </ToolbarItem>
            </ToolbarContent>
        </Toolbar>
    );

    const resultsTable = (
        <Table aria-label="Compatible tools" variant="compact">
            <Thead>
                <Tr>
                    <Th>Tool</Th>
                    <Th>Verdict</Th>
                    <Th>Details</Th>
                </Tr>
            </Thead>
            <Tbody>
                { results?.results?.map(result => {
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
    );

    const spinner = <Spinner size="lg" aria-label="Loading compatibility information" />;

    const content = () => {
        switch (compatibilityViewState(isLoading, isError, results)) {
            case "loading":
                return spinner;
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
                        <ListWithToolbar
                            toolbar={ toolbar }
                            emptyState={ null }
                            filteredEmptyState={ message("None found",
                                `No ${verdictLabel(verdict).text.toLowerCase()} tools were found.`) }
                            isLoading={ isLoading }
                            loadingComponent={ spinner }
                            isError={ false }
                            isFiltered={ true }
                            isEmpty={ (results?.results?.length ?? 0) === 0 }
                        >
                            { resultsTable }
                        </ListWithToolbar>
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
