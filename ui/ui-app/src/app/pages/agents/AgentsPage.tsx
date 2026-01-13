import { FunctionComponent, useEffect, useState } from "react";
import "./AgentsPage.css";
import {
    Button,
    Card,
    CardBody,
    CardTitle,
    EmptyState,
    EmptyStateBody,
    EmptyStateIcon,
    Gallery,
    Label,
    LabelGroup,
    PageSection,
    PageSectionVariants,
    Pagination,
    SearchInput,
    Select,
    SelectOption,
    MenuToggle,
    MenuToggleElement,
    Spinner,
    TextContent,
    Title,
    Toolbar,
    ToolbarContent,
    ToolbarItem,
    Tooltip,
    Flex,
    FlexItem
} from "@patternfly/react-core";
import { SearchIcon, CubesIcon, ExternalLinkAltIcon } from "@patternfly/react-icons";
import { RootPageHeader } from "@app/components";
import { PageDataLoader, PageError, PageErrorHandler, PageProperties, toPageError } from "@app/pages";
import {
    AgentSearchFilters,
    AgentSearchResult,
    AgentSearchResults,
    useAgentService
} from "@services/useAgentService";
import { Paging } from "@models/Paging.ts";
import { useAppNavigation } from "@services/useAppNavigation.ts";
import { FromNow } from "@apicurio/common-ui-components";

export const AGENTS_PAGE_IDX = 5;

const EMPTY_RESULTS: AgentSearchResults = {
    agents: [],
    count: 0
};

const DEFAULT_PAGING: Paging = {
    page: 1,
    pageSize: 12
};

const CAPABILITY_OPTIONS = [
    { value: "", label: "All Capabilities" },
    { value: "streaming", label: "Streaming" },
    { value: "pushNotifications", label: "Push Notifications" }
];

/**
 * The Agents discovery page.
 */
export const AgentsPage: FunctionComponent<PageProperties> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [isSearching, setSearching] = useState<boolean>(false);
    const [results, setResults] = useState<AgentSearchResults>(EMPTY_RESULTS);
    const [paging, setPaging] = useState<Paging>(DEFAULT_PAGING);
    const [nameFilter, setNameFilter] = useState<string>("");
    const [capabilityFilter, setCapabilityFilter] = useState<string>("");
    const [skillFilter, setSkillFilter] = useState<string>("");
    const [capabilitySelectOpen, setCapabilitySelectOpen] = useState(false);

    const agentSvc = useAgentService();
    const appNav = useAppNavigation();

    const createFilters = (): AgentSearchFilters => {
        return {
            name: nameFilter || undefined,
            capability: capabilityFilter || undefined,
            skill: skillFilter || undefined
        };
    };

    const search = async (filters: AgentSearchFilters, paging: Paging): Promise<void> => {
        setSearching(true);
        try {
            const results = await agentSvc.searchAgents(filters, paging);
            setResults(results);
        } catch (error) {
            setPageError(toPageError(error, "Error searching for agents."));
        } finally {
            setSearching(false);
        }
    };

    const createLoaders = (): Promise<any> => {
        return search(createFilters(), paging);
    };

    const handleSearch = (): void => {
        const newPaging = { ...DEFAULT_PAGING };
        setPaging(newPaging);
        search(createFilters(), newPaging);
    };

    const handleKeyPress = (event: React.KeyboardEvent): void => {
        if (event.key === "Enter") {
            handleSearch();
        }
    };

    const handlePageChange = (_event: any, page: number): void => {
        const newPaging = { ...paging, page };
        setPaging(newPaging);
        search(createFilters(), newPaging);
    };

    const handlePerPageChange = (_event: any, perPage: number): void => {
        const newPaging = { page: 1, pageSize: perPage };
        setPaging(newPaging);
        search(createFilters(), newPaging);
    };

    const handleCapabilitySelect = (_event: any, value: string | number): void => {
        setCapabilityFilter(value as string);
        setCapabilitySelectOpen(false);
        const filters: AgentSearchFilters = {
            name: nameFilter || undefined,
            capability: value as string || undefined,
            skill: skillFilter || undefined
        };
        search(filters, { ...DEFAULT_PAGING });
        setPaging({ ...DEFAULT_PAGING });
    };

    const navigateToAgent = (agent: AgentSearchResult): void => {
        const gid = encodeURIComponent(agent.groupId || "default");
        const aid = encodeURIComponent(agent.artifactId);
        appNav.navigateTo(`/explore/${gid}/${aid}`);
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, []);

    const renderAgentCard = (agent: AgentSearchResult): React.ReactElement => {
        return (
            <Card
                key={`${agent.groupId}-${agent.artifactId}`}
                className="agent-card"
                isClickable
                onClick={() => navigateToAgent(agent)}
            >
                <CardTitle>
                    <Flex>
                        <FlexItem>
                            <span className="agent-name">{agent.name}</span>
                        </FlexItem>
                        {agent.version && (
                            <FlexItem align={{ default: "alignRight" }}>
                                <Label color="blue" isCompact>v{agent.version}</Label>
                            </FlexItem>
                        )}
                    </Flex>
                </CardTitle>
                <CardBody>
                    <div className="agent-description">
                        {agent.description || <span className="no-description">No description</span>}
                    </div>

                    {agent.url && (
                        <div className="agent-url">
                            <a href={agent.url} target="_blank" rel="noopener noreferrer" onClick={e => e.stopPropagation()}>
                                {agent.url}
                                <ExternalLinkAltIcon className="external-icon" />
                            </a>
                        </div>
                    )}

                    {/* Capabilities */}
                    {agent.capabilities && (
                        <div className="agent-capabilities">
                            <LabelGroup>
                                {agent.capabilities.streaming && (
                                    <Label color="green" isCompact>Streaming</Label>
                                )}
                                {agent.capabilities.pushNotifications && (
                                    <Label color="green" isCompact>Push Notifications</Label>
                                )}
                            </LabelGroup>
                        </div>
                    )}

                    {/* Skills */}
                    {agent.skills && agent.skills.length > 0 && (
                        <div className="agent-skills">
                            <LabelGroup numLabels={3}>
                                {agent.skills.map((skill, index) => (
                                    <Tooltip key={index} content={skill}>
                                        <Label color="purple" isCompact>{skill}</Label>
                                    </Tooltip>
                                ))}
                            </LabelGroup>
                        </div>
                    )}

                    {/* Metadata */}
                    <div className="agent-metadata">
                        <span className="metadata-item">
                            <strong>Group:</strong> {agent.groupId || "default"}
                        </span>
                        {agent.createdOn && (
                            <span className="metadata-item">
                                <strong>Created:</strong> <FromNow date={new Date(agent.createdOn)} />
                            </span>
                        )}
                        {agent.owner && (
                            <span className="metadata-item">
                                <strong>Owner:</strong> {agent.owner}
                            </span>
                        )}
                    </div>
                </CardBody>
            </Card>
        );
    };

    const renderEmptyState = (): React.ReactElement => {
        const isFiltered = !!(nameFilter || capabilityFilter || skillFilter);
        return (
            <EmptyState>
                <EmptyStateIcon icon={isFiltered ? SearchIcon : CubesIcon} />
                <Title headingLevel="h4" size="lg">
                    {isFiltered ? "No agents found" : "No agents registered"}
                </Title>
                <EmptyStateBody>
                    {isFiltered
                        ? "No agents match your search criteria. Try adjusting your filters."
                        : "There are no A2A Agent Cards registered in the registry yet. Register an agent card artifact to see it here."}
                </EmptyStateBody>
            </EmptyState>
        );
    };

    const renderToolbar = (): React.ReactElement => {
        return (
            <Toolbar>
                <ToolbarContent>
                    <ToolbarItem>
                        <SearchInput
                            placeholder="Search by name..."
                            value={nameFilter}
                            onChange={(_event, value) => setNameFilter(value)}
                            onSearch={handleSearch}
                            onClear={() => {
                                setNameFilter("");
                                const filters: AgentSearchFilters = {
                                    capability: capabilityFilter || undefined,
                                    skill: skillFilter || undefined
                                };
                                search(filters, { ...DEFAULT_PAGING });
                                setPaging({ ...DEFAULT_PAGING });
                            }}
                            onKeyDown={handleKeyPress}
                        />
                    </ToolbarItem>
                    <ToolbarItem>
                        <SearchInput
                            placeholder="Filter by skill..."
                            value={skillFilter}
                            onChange={(_event, value) => setSkillFilter(value)}
                            onSearch={handleSearch}
                            onClear={() => {
                                setSkillFilter("");
                                const filters: AgentSearchFilters = {
                                    name: nameFilter || undefined,
                                    capability: capabilityFilter || undefined
                                };
                                search(filters, { ...DEFAULT_PAGING });
                                setPaging({ ...DEFAULT_PAGING });
                            }}
                            onKeyDown={handleKeyPress}
                        />
                    </ToolbarItem>
                    <ToolbarItem>
                        <Select
                            isOpen={capabilitySelectOpen}
                            onOpenChange={setCapabilitySelectOpen}
                            toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                                <MenuToggle
                                    ref={toggleRef}
                                    onClick={() => setCapabilitySelectOpen(!capabilitySelectOpen)}
                                    isExpanded={capabilitySelectOpen}
                                >
                                    {CAPABILITY_OPTIONS.find(o => o.value === capabilityFilter)?.label || "All Capabilities"}
                                </MenuToggle>
                            )}
                            onSelect={handleCapabilitySelect}
                            selected={capabilityFilter}
                        >
                            {CAPABILITY_OPTIONS.map(option => (
                                <SelectOption key={option.value} value={option.value}>
                                    {option.label}
                                </SelectOption>
                            ))}
                        </Select>
                    </ToolbarItem>
                    <ToolbarItem>
                        <Button variant="primary" onClick={handleSearch}>
                            Search
                        </Button>
                    </ToolbarItem>
                    <ToolbarItem variant="pagination" align={{ default: "alignRight" }}>
                        <Pagination
                            itemCount={results.count}
                            perPage={paging.pageSize}
                            page={paging.page}
                            onSetPage={handlePageChange}
                            onPerPageSelect={handlePerPageChange}
                            variant="top"
                            isCompact
                        />
                    </ToolbarItem>
                </ToolbarContent>
            </Toolbar>
        );
    };

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection className="ps_agents-header" variant={PageSectionVariants.light} padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={AGENTS_PAGE_IDX} />
                </PageSection>
                <PageSection className="ps_agents-description" variant={PageSectionVariants.light}>
                    <TextContent>
                        <Title headingLevel="h1">A2A Agent Discovery</Title>
                        <p>
                            Discover and explore registered A2A Agent Cards. Search by name, filter by capabilities, or find agents with specific skills.
                        </p>
                    </TextContent>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    {renderToolbar()}
                    {isSearching ? (
                        <div className="loading-container">
                            <Spinner size="xl" />
                        </div>
                    ) : results.count === 0 ? (
                        renderEmptyState()
                    ) : (
                        <Gallery hasGutter className="agents-gallery">
                            {results.agents.map(agent => renderAgentCard(agent))}
                        </Gallery>
                    )}
                    {results.count > 0 && (
                        <Pagination
                            itemCount={results.count}
                            perPage={paging.pageSize}
                            page={paging.page}
                            onSetPage={handlePageChange}
                            onPerPageSelect={handlePerPageChange}
                            variant="bottom"
                            className="bottom-pagination"
                        />
                    )}
                </PageSection>
            </PageDataLoader>
        </PageErrorHandler>
    );
};
