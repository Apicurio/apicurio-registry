import { FunctionComponent, useEffect, useState } from "react";
import "./AgentDetailPage.css";
import {
    Breadcrumb,
    BreadcrumbItem,
    Button,
    CodeBlock,
    CodeBlockCode,
    Content,
    Flex,
    FlexItem,
    PageSection,
    ToggleGroup,
    ToggleGroupItem,
    Tooltip
} from "@patternfly/react-core";
import { CopyIcon, ExternalLinkAltIcon, CodeIcon, EyeIcon, PencilAltIcon } from "@patternfly/react-icons";
import { Link, useParams } from "react-router";
import { AGENTS_PAGE_IDX, PageDataLoader, PageError, PageErrorHandler, PageProperties, toPageError } from "@app/pages";
import { RootPageHeader } from "@app/components";
import { AgentCard, AgentCardViewer } from "@app/components/agentCard";
import { EditAgentModal } from "@app/pages/agents/components";
import { useAgentService } from "@services/useAgentService.ts";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { useConfigService } from "@services/useConfigService.ts";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { PleaseWaitModal } from "@apicurio/common-ui-components";

export const AgentDetailPage: FunctionComponent<PageProperties> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [agentCard, setAgentCard] = useState<AgentCard>();
    const [rawJson, setRawJson] = useState<string>("");
    const [showRawJson, setShowRawJson] = useState<boolean>(false);
    const [copyTooltip, setCopyTooltip] = useState<string>("Copy Agent Card URL");
    const [isEditModalOpen, setIsEditModalOpen] = useState(false);
    const [isPleaseWaitModalOpen, setIsPleaseWaitModalOpen] = useState(false);

    const agentSvc = useAgentService();
    const appNavigation: AppNavigation = useAppNavigation();
    const config = useConfigService();
    const groups: GroupsService = useGroupsService();
    const { groupId, artifactId } = useParams();

    const getBaseUrl = (): string => {
        const artifactsUrl = config.artifactsUrl();
        const url = artifactsUrl.endsWith("/") ? artifactsUrl.slice(0, -1) : artifactsUrl;
        const suffix = "/apis/registry/v3";
        if (url.endsWith(suffix)) {
            return url.slice(0, -suffix.length);
        }
        try {
            return new URL(url).origin;
        } catch {
            return window.location.origin;
        }
    };

    const createLoaders = (): Promise<any> => {
        const gid = decodeURIComponent(groupId || "default");
        const aid = decodeURIComponent(artifactId || "");
        return agentSvc.getAgentCard(gid, aid).then(card => {
            setAgentCard(card as AgentCard);
            setRawJson(JSON.stringify(card, null, 2));
        }).catch(error => {
            setPageError(toPageError(error, "Error loading agent card."));
        });
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, [groupId, artifactId]);

    const handleCopyUrl = (): void => {
        const gid = encodeURIComponent(groupId || "default");
        const aid = encodeURIComponent(artifactId || "");
        const url = `${getBaseUrl()}/.well-known/agents/${gid}/${aid}`;
        navigator.clipboard.writeText(url).then(() => {
            setCopyTooltip("Copied!");
            setTimeout(() => setCopyTooltip("Copy Agent Card URL"), 2000);
        });
    };

    const handleViewInRegistry = (): void => {
        const gid = encodeURIComponent(groupId || "default");
        const aid = encodeURIComponent(artifactId || "");
        appNavigation.navigateTo(`/explore/${gid}/${aid}`);
    };

    const doSaveAgentCard = (updatedCard: AgentCard): void => {
        setIsEditModalOpen(false);
        setIsPleaseWaitModalOpen(true);
        const gid = decodeURIComponent(groupId || "default");
        const aid = decodeURIComponent(artifactId || "");
        groups.createArtifactVersion(gid, aid, {
            content: {
                content: JSON.stringify(updatedCard, null, 2),
                contentType: "application/json"
            }
        }).then(() => {
            setIsPleaseWaitModalOpen(false);
            setAgentCard(updatedCard);
            setRawJson(JSON.stringify(updatedCard, null, 2));
        }).catch(error => {
            setIsPleaseWaitModalOpen(false);
            setPageError(toPageError(error, "Error saving agent card."));
        });
    };

    const gid: string = groupId || "default";
    const breadcrumbs = (
        <Breadcrumb>
            <BreadcrumbItem>
                <Link to={appNavigation.createLink("/agents")}>Agents</Link>
            </BreadcrumbItem>
            <BreadcrumbItem>
                {decodeURIComponent(gid)}
            </BreadcrumbItem>
            <BreadcrumbItem isActive={true}>
                {decodeURIComponent(artifactId as string)}
            </BreadcrumbItem>
        </Breadcrumb>
    );

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection hasBodyWrapper={false} className="ps_agents-header" padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={AGENTS_PAGE_IDX} />
                </PageSection>
                <PageSection hasBodyWrapper={false} className="ps_header-breadcrumbs" children={breadcrumbs} />
                <PageSection hasBodyWrapper={false} className="ps_agent-detail-header">
                    <Flex>
                        <FlexItem>
                            <Content>
                                <h1>{agentCard?.name || decodeURIComponent(artifactId || "")}</h1>
                                {agentCard?.description && (
                                    <p>{agentCard.description}</p>
                                )}
                            </Content>
                        </FlexItem>
                        <FlexItem align={{ default: "alignRight" }}>
                            <Flex>
                                <FlexItem>
                                    <ToggleGroup aria-label="View mode">
                                        <ToggleGroupItem
                                            icon={<EyeIcon />}
                                            text="Card View"
                                            isSelected={!showRawJson}
                                            onChange={() => setShowRawJson(false)}
                                        />
                                        <ToggleGroupItem
                                            icon={<CodeIcon />}
                                            text="Raw JSON"
                                            isSelected={showRawJson}
                                            onChange={() => setShowRawJson(true)}
                                        />
                                    </ToggleGroup>
                                </FlexItem>
                                <FlexItem>
                                    <Button variant="secondary" icon={<PencilAltIcon />} onClick={() => setIsEditModalOpen(true)}>
                                        Edit
                                    </Button>
                                </FlexItem>
                                <FlexItem>
                                    <Tooltip content={copyTooltip}>
                                        <Button variant="secondary" icon={<CopyIcon />} onClick={handleCopyUrl}>
                                            Copy URL
                                        </Button>
                                    </Tooltip>
                                </FlexItem>
                                <FlexItem>
                                    <Button variant="secondary" icon={<ExternalLinkAltIcon />} onClick={handleViewInRegistry}>
                                        View in Registry
                                    </Button>
                                </FlexItem>
                            </Flex>
                        </FlexItem>
                    </Flex>
                </PageSection>
                <PageSection hasBodyWrapper={false} isFilled={true} className="ps_agent-detail-content">
                    {agentCard && !showRawJson && (
                        <AgentCardViewer agentCard={agentCard} />
                    )}
                    {showRawJson && (
                        <CodeBlock>
                            <CodeBlockCode>{rawJson}</CodeBlockCode>
                        </CodeBlock>
                    )}
                </PageSection>
            </PageDataLoader>
            {agentCard && (
                <EditAgentModal
                    isOpen={isEditModalOpen}
                    agentCard={agentCard}
                    onClose={() => setIsEditModalOpen(false)}
                    onSave={doSaveAgentCard}
                />
            )}
            <PleaseWaitModal
                message="Saving agent card, please wait..."
                isOpen={isPleaseWaitModalOpen}
            />
        </PageErrorHandler>
    );
};
