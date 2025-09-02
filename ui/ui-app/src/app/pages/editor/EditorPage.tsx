import { FunctionComponent, useEffect, useState } from "react";
import "./EditorPage.css";
import { Breadcrumb, BreadcrumbItem, PageSection, PageSectionVariants } from "@patternfly/react-core";
import { Link, useParams } from "react-router-dom";
import { EXPLORE_PAGE_IDX, PageDataLoader, PageError, PageErrorHandler, PageProperties } from "@app/pages";
import { IfFeature, RootPageHeader } from "@app/components";
import { ContentTypes } from "@models/ContentTypes.ts";
import { PleaseWaitModal } from "@apicurio/common-ui-components";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { Draft, DraftContent } from "@models/drafts";


/**
 * The editor page.
 */
export const EditorPage: FunctionComponent<PageProperties> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [draft, setDraft] = useState<Draft>({
        createdBy: "",
        createdOn: new Date(),
        description: undefined,
        draftId: "",
        groupId: "",
        labels: {},
        modifiedBy: "",
        modifiedOn: new Date(),
        contentId: 0,
        name: "",
        type: "",
        version: ""
    });
    const [draftContent, setDraftContent] = useState<DraftContent>({
        content: "",
        contentType: ContentTypes.APPLICATION_JSON,
    });
    const [originalContent, setOriginalContent] = useState<any>();
    const [currentContent, setCurrentContent] = useState<any>();
    const [isDirty, setDirty] = useState(false);
    const [isCompareModalOpen, setCompareModalOpen] = useState(false);
    const [isPleaseWaitModalOpen, setPleaseWaitModalOpen] = useState(false);
    const [isConfirmOverwriteModalOpen, setConfirmOverwriteModalOpen] = useState(false);
    const [pleaseWaitMessage, setPleaseWaitMessage] = useState("");
    const [isContentConflicting, setIsContentConflicting] = useState(false);

    const appNavigation: AppNavigation = useAppNavigation();
    const logger: LoggerService = useLoggerService();
    const groups: GroupsService = useGroupsService();
    const { groupId, artifactId, version }= useParams();

    const createLoaders = (): Promise<any>[] => {
        let gid: string|null = groupId as string;
        if (gid == "default") {
            gid = null;
        }
        logger.info("Loading data for draft: ", artifactId, version);
        return [
        ];
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, [groupId, artifactId, version]);

    const gid: string = encodeURIComponent(groupId || "default");
    const aid: string = encodeURIComponent(artifactId || "");
    const ver: string = encodeURIComponent(version || "");
    const breadcrumbs = (
        <Breadcrumb>
            <BreadcrumbItem><Link to={appNavigation.createLink("/explore")} data-testid="breadcrumb-lnk-explore">Explore</Link></BreadcrumbItem>
            <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ gid }`)}
                data-testid="breadcrumb-lnk-group">{ gid }</Link></BreadcrumbItem>
            <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ gid }/${ aid }`)}
                data-testid="breadcrumb-lnk-artifact">{ artifactId }</Link></BreadcrumbItem>
            <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ gid }/${ aid }/versions/${ ver }`)}
                data-testid="breadcrumb-lnk-version">{ version as string }</Link></BreadcrumbItem>
            <BreadcrumbItem isActive={true}>Editor</BreadcrumbItem>
        </Breadcrumb>
    );

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection className="ps_explore-header" variant={PageSectionVariants.light} padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={EXPLORE_PAGE_IDX} />
                </PageSection>
                <IfFeature feature="breadcrumbs" is={true}>
                    <PageSection className="ps_header-breadcrumbs" variant={PageSectionVariants.light} children={breadcrumbs} />
                </IfFeature>
                <PageSection className="ps_editor-header" variant={PageSectionVariants.light}>
                    <h1>Editor TBD</h1>
                </PageSection>
            </PageDataLoader>
            <PleaseWaitModal message={pleaseWaitMessage}
                isOpen={isPleaseWaitModalOpen} />
        </PageErrorHandler>
    );

};
