import { FunctionComponent, useEffect, useState } from "react";
import "./AgentsPage.css";
import {
    Alert,
    AlertActionCloseButton,
    Button,
    Card,
    CardBody,
    CardTitle,
    EmptyState,
    EmptyStateBody,
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
    Switch,
    Content,
    Toolbar,
    ToolbarContent,
    ToolbarItem,
    Tooltip,
    Flex,
    FlexItem
} from "@patternfly/react-core";
import { SearchIcon, CubesIcon, ExternalLinkAltIcon } from "@patternfly/react-icons";
import { IfFeature, RootPageHeader } from "@app/components";
import { AGENTS_PAGE_IDX, PageDataLoader, PageError, PageErrorHandler, PageProperties, toPageError } from "@app/pages";
import {
    AgentSearchFilters,
    AgentSearchResult,
    AgentSearchResults,
    useAgentService
} from "@services/useAgentService";
import { Paging } from "@models/Paging.ts";
import { useAppNavigation } from "@services/useAppNavigation.ts";
import { FromNow, PleaseWaitModal } from "@apitomy/common-ui-components";
import {
    CreateAgentModal,
    FederatedSources,
    ImportAgentModal,
    LOCAL_SOURCE,
    sourceName
} from "@app/pages/agents/components";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import {
    CreateArtifact,
    FederatedAgentSearchResult,
    FederatedSearchSource
} from "@sdk/lib/generated-client/models";
import { isErrorStatus } from "@utils/rest.utils.ts";

const EMPTY_RESULTS: AgentSearchResults = {
    agents: [],
    count: 0
};

const DEFAULT_PAGING: Paging = {
    page: 1,
    pageSize: 12
};

/**
 * An agent as shown on the page. The source is set only when remote registries were searched.
 */
type ShownAgent = AgentSearchResult & {
    source?: string;
};

const toShownAgent = (agent: FederatedAgentSearchResult): ShownAgent => {
    return {
        source: agent.source || LOCAL_SOURCE,
        groupId: agent.groupId || "",
        artifactId: agent.artifactId || "",
        name: agent.name || "",
        description: agent.description || undefined,
        supportedInterfaces: agent.supportedInterfaces?.map(iface => ({
            url: iface.url || "",
            protocolBinding: iface.protocolBinding || "",
            protocolVersion: iface.protocolVersion || undefined
        })),
        skills: agent.skills || undefined,
        capabilities: agent.capabilities ? {
            streaming: agent.capabilities.streaming || undefined,
            pushNotifications: agent.capabilities.pushNotifications || undefined
        } : undefined,
        createdOn: agent.createdOn || undefined,
        owner: agent.owner || undefined
    };
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
    const [includeRemote, setIncludeRemote] = useState<boolean>(false);
    const [sources, setSources] = useState<FederatedSearchSource[]>();
    const [remoteProblem, setRemoteProblem] = useState<string>();
    const [paging, setPaging] = useState<Paging>(DEFAULT_PAGING);
    const [appliedFilters, setAppliedFilters] = useState<AgentSearchFilters>({});
    const [nameFilter, setNameFilter] = useState<string>("");
    const [capabilityFilter, setCapabilityFilter] = useState<string>("");
    const [skillFilter, setSkillFilter] = useState<string>("");
    const [capabilitySelectOpen, setCapabilitySelectOpen] = useState(false);

    const [isCreateAgentModalOpen, setIsCreateAgentModalOpen] = useState(false);
    const [isImportAgentModalOpen, setIsImportAgentModalOpen] = useState(false);
    const [isPleaseWaitModalOpen, setIsPleaseWaitModalOpen] = useState(false);
    const [pleaseWaitMessage, setPleaseWaitMessage] = useState("");

    const agentSvc = useAgentService();
    const appNav = useAppNavigation();
    const groups: GroupsService = useGroupsService();

    const createFilters = (): AgentSearchFilters => {
        return {
            name: nameFilter || undefined,
            capability: capabilityFilter || undefined,
            skill: skillFilter || undefined
        };
    };

    const search = async (filters: AgentSearchFilters, paging: Paging, remote: boolean = includeRemote): Promise<void> => {
        setAppliedFilters(filters);
        setSearching(true);
        try {
            if (remote) {
                // No paging across registries: each one returns up to a page of agents.
                const federated = await agentSvc.searchFederatedAgents(filters, paging.pageSize);
                const agents: ShownAgent[] = (federated.agents || []).map(toShownAgent);
                setResults({ agents, count: agents.length });
                setSources(federated.sources || []);
            } else {
                setResults(await agentSvc.searchAgents(filters, paging));
                setSources(undefined);
            }
        } catch (error) {
            if (remote && isErrorStatus(error, 409)) {
                // Federation is not enabled, or too many peers are: say why and show this registry's agents.
                setRemoteProblem((error as any)?.detail || "Remote registries could not be searched.");
                setIncludeRemote(false);
                await search(filters, paging, false);
                return;
            }
            setPageError(toPageError(error, "Error searching for agents."));
        } finally {
            setSearching(false);
        }
    };

    const applyFilters = (newFilters: AgentSearchFilters): void => {
        const newPaging = { ...DEFAULT_PAGING, pageSize: paging.pageSize };
        setPaging(newPaging);
        search(newFilters, newPaging);
    };

    const createLoaders = (): Promise<any> => {
        return search(createFilters(), paging);
    };

    const handleIncludeRemote = (_event: React.FormEvent<HTMLInputElement>, checked: boolean): void => {
        setIncludeRemote(checked);
        setRemoteProblem(undefined);
        const newPaging = { ...DEFAULT_PAGING, pageSize: paging.pageSize };
        setPaging(newPaging);
        search(appliedFilters, newPaging, checked);
    };

    const handleSearch = (): void => {
        applyFilters(createFilters());
    };

    const handleKeyPress = (event: React.KeyboardEvent): void => {
        if (event.key === "Enter") {
            handleSearch();
        }
    };

    const handlePageChange = (_event: any, page: number): void => {
        const newPaging = { ...paging, page };
        setPaging(newPaging);
        search(appliedFilters, newPaging);
    };

    const handlePerPageChange = (_event: any, perPage: number): void => {
        const newPaging = { page: 1, pageSize: perPage };
        setPaging(newPaging);
        search(appliedFilters, newPaging);
    };

    const handleCapabilitySelect = (_event: React.MouseEvent | undefined, value: string | number | undefined): void => {
        const selectedValue = value as string || "";
        setCapabilityFilter(selectedValue);
        setCapabilitySelectOpen(false);
        applyFilters({
            ...appliedFilters,
            capability: selectedValue || undefined
        });
    };

    const navigateToAgent = (agent: AgentSearchResult): void => {
        const gid = encodeURIComponent(agent.groupId || "default");
        const aid = encodeURIComponent(agent.artifactId);
        appNav.navigateTo(`/explore/${gid}/${aid}`);
    };

    const doSaveAgent = (groupId: string, data: CreateArtifact, waitMessage: string, errorMessage: string): void => {
        setPleaseWaitMessage(waitMessage);
        setIsPleaseWaitModalOpen(true);
        groups.createArtifact(groupId || null, data).then(response => {
            const gid = encodeURIComponent(response.artifact?.groupId || groupId || "default");
            const aid = encodeURIComponent(response.artifact?.artifactId || data.artifactId || "");
            setIsPleaseWaitModalOpen(false);
            appNav.navigateTo(`/explore/${gid}/${aid}`);
        }).catch(error => {
            setIsPleaseWaitModalOpen(false);
            setPageError(toPageError(error, errorMessage));
        });
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, []);

    const renderAgentCard = (agent: ShownAgent): React.ReactElement => {
        const primaryInterface = agent.supportedInterfaces?.[0];
        // An agent from a peer registry has no page here.
        const isRemote = agent.source !== undefined && agent.source !== LOCAL_SOURCE;
        return (
            <Card
                key={`${agent.source || LOCAL_SOURCE}-${agent.groupId}-${agent.artifactId}`}
                className={isRemote ? "agent-card agent-card-remote" : "agent-card"}
                data-testid={`agent-card-${agent.source || LOCAL_SOURCE}-${agent.artifactId}`}
                onClick={isRemote ? undefined : () => navigateToAgent(agent)}
            >
                <CardTitle>
                    <Flex>
                        <FlexItem>
                            <span className="agent-name">{agent.name || agent.artifactId}</span>
                        </FlexItem>
                        {agent.source !== undefined && (
                            <FlexItem>
                                <Label color={isRemote ? "purple" : "grey"} isCompact data-testid="agent-source">
                                    {sourceName(agent.source)}
                                </Label>
                            </FlexItem>
                        )}
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

                    {primaryInterface?.url && (
                        <div className="agent-url">
                            <a href={primaryInterface.url} target="_blank" rel="noopener noreferrer" onClick={e => e.stopPropagation()}>
                                {primaryInterface.url}
                                <ExternalLinkAltIcon className="external-icon" />
                            </a>
                            {primaryInterface.protocolBinding && (
                                <Label color="teal" isCompact className="agent-protocol-binding">
                                    {primaryInterface.protocolBinding}
                                </Label>
                            )}
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
        const isFiltered = !!(appliedFilters.name || appliedFilters.capability || appliedFilters.skill);
        if (sources) {
            return (
                <EmptyState headingLevel="h4" icon={SearchIcon} titleText="No agents found">
                    <EmptyStateBody>
                        No agent in this registry or in the remote registries that answered matches your search.
                    </EmptyStateBody>
                </EmptyState>
            );
        }
        return (
            <EmptyState
                headingLevel="h4"
                icon={isFiltered ? SearchIcon : CubesIcon}
                titleText={isFiltered ? "No agents found" : "No agents registered"}>
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
                                applyFilters({
                                    ...appliedFilters,
                                    name: undefined
                                });
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
                                applyFilters({
                                    ...appliedFilters,
                                    skill: undefined
                                });
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
                    <ToolbarItem alignSelf="center">
                        <Switch
                            id="include-remote-registries"
                            data-testid="include-remote-registries"
                            label="Include remote registries"
                            isChecked={includeRemote}
                            onChange={handleIncludeRemote}
                        />
                    </ToolbarItem>
                    <IfFeature feature="readOnly" isNot={true}>
                        <ToolbarItem>
                            <Button variant="secondary" onClick={() => setIsCreateAgentModalOpen(true)}>
                                Create Agent
                            </Button>
                        </ToolbarItem>
                        <ToolbarItem>
                            <Button variant="secondary" onClick={() => setIsImportAgentModalOpen(true)}>
                                Import Agent
                            </Button>
                        </ToolbarItem>
                    </IfFeature>
                    {!sources && (
                        <ToolbarItem variant="pagination" align={{ default: "alignEnd" }}>
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
                    )}
                </ToolbarContent>
            </Toolbar>
        );
    };

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection hasBodyWrapper={false} className="ps_agents-header"  padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={AGENTS_PAGE_IDX} />
                </PageSection>
                <PageSection hasBodyWrapper={false} className="ps_agents-description" >
                    <Content>
                        <h1>A2A Agent Discovery</h1>
                        <p>
                            Discover and explore registered A2A Agent Cards. Search by name, filter by capabilities, or find agents with specific skills.
                        </p>
                    </Content>
                </PageSection>
                <PageSection hasBodyWrapper={false} variant={PageSectionVariants.default} isFilled={true}>
                    {renderToolbar()}
                    {remoteProblem && (
                        <Alert variant="info" isInline title="Only this registry was searched"
                            className="remote-problem" data-testid="remote-problem"
                            actionClose={<AlertActionCloseButton onClose={() => setRemoteProblem(undefined)} />}>
                            {remoteProblem}
                        </Alert>
                    )}
                    {sources && !isSearching && (
                        <div className="federated-summary">
                            <FederatedSources sources={sources} />
                            <Content component="small">
                                Up to {paging.pageSize} agents from each registry. Agents from remote registries are
                                public ones only, and open on their own registry.
                            </Content>
                        </div>
                    )}
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
                    {results.count > 0 && !sources && (
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
            <CreateAgentModal
                isOpen={isCreateAgentModalOpen}
                onClose={() => setIsCreateAgentModalOpen(false)}
                onCreate={(groupId, data) => {
                    setIsCreateAgentModalOpen(false);
                    doSaveAgent(groupId, data, "Creating agent card, please wait...", "Error creating agent.");
                }}
            />
            <ImportAgentModal
                isOpen={isImportAgentModalOpen}
                onClose={() => setIsImportAgentModalOpen(false)}
                onImport={(groupId, data) => {
                    setIsImportAgentModalOpen(false);
                    doSaveAgent(groupId, data, "Importing agent card, please wait...", "Error importing agent.");
                }}
            />
            <PleaseWaitModal
                message={pleaseWaitMessage}
                isOpen={isPleaseWaitModalOpen}
            />
        </PageErrorHandler>
    );
};
