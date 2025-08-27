import { FunctionComponent, useEffect, useState } from "react";
import "./ExplorePage.css";
import { PageSection, PageSectionVariants, TextContent } from "@patternfly/react-core";
import {
    EXPLORE_PAGE_IDX,
    ExploreGroupList,
    ExplorePageEmptyState,
    ExplorePageToolbar,
    ExplorePageToolbarFilterCriteria, ImportModal,
    PageDataLoader,
    PageError,
    PageErrorHandler,
    toPageError
} from "@app/pages";
import { CreateGroupModal, InvalidContentModal, RootPageHeader } from "@app/components";
import { ListWithToolbar, PleaseWaitModal, ProgressModal } from "@apicurio/common-ui-components";
import { FilterBy, SearchFilter, SearchService, useSearchService } from "@services/useSearchService.ts";
import { GroupSearchResults } from "@apicurio/apicurio-registry-sdk/dist/generated-client/models";
import { Paging } from "@models/Paging.ts";
import { GroupsSortBy } from "@models/GroupsSortBy.ts";
import { SortOrder } from "@models/SortOrder.ts";
import { CreateGroup, RuleViolationProblemDetails } from "@sdk/lib/generated-client/models";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { AdminService, useAdminService } from "@services/useAdminService.ts";

/**
 * Properties
 */
export type ExplorePageProps = object;

const EMPTY_RESULTS: GroupSearchResults = {
    groups: [],
    count: 0
};

const DEFAULT_PAGING: Paging = {
    page: 1,
    pageSize: 10
};

/**
 * The Explore page.
 */
export const ExplorePage: FunctionComponent<ExplorePageProps> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [isPleaseWaitModalOpen, setPleaseWaitModalOpen] = useState<boolean>(false);
    const [pleaseWaitMessage, setPleaseWaitMessage] = useState("");
    const [criteria, setCriteria] = useState<ExplorePageToolbarFilterCriteria>({
        filterBy: FilterBy.name,
        filterValue: "",
        ascending: true
    });
    const [isSearching, setSearching] = useState<boolean>(false);
    const [paging, setPaging] = useState<Paging>(DEFAULT_PAGING);
    const [results, setResults] = useState<GroupSearchResults>(EMPTY_RESULTS);
    const [isCreateGroupModalOpen, setCreateGroupModalOpen] = useState<boolean>(false);
    const [invalidContentError, setInvalidContentError] = useState<RuleViolationProblemDetails>();
    const [isInvalidContentModalOpen, setInvalidContentModalOpen] = useState<boolean>(false);
    const [isImportModalOpen, setImportModalOpen] = useState<boolean>(false);
    const [isImporting, setImporting] = useState(false);
    const [importProgress, setImportProgress] = useState(0);

    const appNavigation: AppNavigation = useAppNavigation();
    const searchSvc: SearchService = useSearchService();
    const groups: GroupsService = useGroupsService();
    const logger: LoggerService = useLoggerService();
    const admin: AdminService = useAdminService();

    const createLoaders = (): Promise<any> => {
        return search(criteria, paging);
    };


    const onResultsLoaded = (results: GroupSearchResults): void => {
        setSearching(false);
        setResults(results);
    };

    const onFilterCriteriaChange = (newCriteria: ExplorePageToolbarFilterCriteria): void => {
        setCriteria(newCriteria);
        search(newCriteria, paging);
    };

    const isFiltered = (): boolean => {
        return !!criteria.filterValue;
    };

    const search = async (criteria: ExplorePageToolbarFilterCriteria, paging: Paging): Promise<any> => {
        setSearching(true);
        const filters: SearchFilter[] = [
            {
                by: criteria.filterBy,
                value: criteria.filterValue
            }
        ];

        const sortOrder: SortOrder = criteria.ascending ? SortOrder.asc : SortOrder.desc;
        return searchSvc.searchGroups(filters, GroupsSortBy.groupId, sortOrder, paging).then(results => {
            onResultsLoaded(results);
        }).catch(error => {
            setPageError(toPageError(error, "Error exploring groups."));
        });
    };

    const onSetPage = (_event: any, newPage: number, perPage?: number): void => {
        const newPaging: Paging = {
            page: newPage,
            pageSize: perPage ? perPage : paging.pageSize
        };
        setPaging(newPaging);
        search(criteria, newPaging);
    };

    const onPerPageSelect = (_event: any, newPerPage: number): void => {
        const newPaging: Paging = {
            page: paging.page,
            pageSize: newPerPage
        };
        setPaging(newPaging);
        search(criteria, newPaging);
    };

    const handleInvalidContentError = (error: any): void => {
        logger.info("[SearchPage] Invalid content error:", error);
        setInvalidContentError(error);
        setInvalidContentModalOpen(true);
    };

    const pleaseWait = (isOpen: boolean, message: string = ""): void => {
        setPleaseWaitModalOpen(isOpen);
        setPleaseWaitMessage(message);
    };

    const closeInvalidContentModal = (): void => {
        setInvalidContentModalOpen(false);
    };

    const onCreateGroup = (): void => {
        setCreateGroupModalOpen(true);
    };

    const doCreateGroup = (data: CreateGroup): void => {
        setCreateGroupModalOpen(false);
        pleaseWait(true);

        groups.createGroup(data).then(response => {
            const groupId: string = response.groupId!;
            const groupLocation: string = `/explore/${ encodeURIComponent(groupId) }`;
            logger.info("[SearchPage] Group successfully created.  Redirecting to details page: ", groupLocation);
            appNavigation.navigateTo(groupLocation);
        }).catch( error => {
            pleaseWait(false);
            if (error && (error.status === 400 || error.status === 409)) {
                handleInvalidContentError(error);
            } else {
                setPageError(toPageError(error, "Error creating group."));
            }
        });
    };

    const onImportArtifacts = (): void => {
        setImportModalOpen(true);
    };

    const onExportArtifacts = (): void => {
        admin.exportAs("all-artifacts.zip").then(dref => {
            const link = document.createElement("a");
            link.href = dref.href || "";
            link.download = "all-artifacts.zip";
            link.click();
        }).catch(error => {
            setPageError(toPageError(error, "Failed to export artifacts"));
        });
    };

    const onImportModalClose = (): void => {
        setImportModalOpen(false);
    };

    const doImport = (file: File | undefined): void => {
        setImporting(true);
        setImportProgress(0);
        setImportModalOpen(false);

        if (file != null) {
            admin.importFrom(file, (event: any) => {
                let progress: number = 0;
                if (event.lengthComputable) {
                    progress = Math.round(100 * (event.loaded / event.total));
                }
                setImportProgress(progress);
            }).then(() => {
                setTimeout(() => {
                    setImporting(false);
                    setImportProgress(100);
                    setImportModalOpen(false);
                    search(criteria, paging);
                }, 1500);
            }).catch(error => {
                setPageError(toPageError(error, "Error importing multiple artifacts"));
            });
        }
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, []);

    const toolbar = (
        <ExplorePageToolbar
            results={results}
            criteria={criteria}
            paging={paging}
            onPerPageSelect={onPerPageSelect}
            onSetPage={onSetPage}
            onCriteriaChange={onFilterCriteriaChange}
            onCreateGroup={onCreateGroup}
            onExport={onExportArtifacts}
            onImport={onImportArtifacts}
        />
    );

    const emptyState = (
        <ExplorePageEmptyState isFiltered={isFiltered()} />
    );

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection className="ps_explore-header" variant={PageSectionVariants.light} padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={EXPLORE_PAGE_IDX} />
                </PageSection>
                <PageSection className="ps_explore-description" variant={PageSectionVariants.light}>
                    <TextContent>
                        Explore the contents of the Registry by exploring <b>Groups</b> below, then navigating
                        the results.
                    </TextContent>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    <ListWithToolbar toolbar={toolbar}
                        emptyState={emptyState}
                        filteredEmptyState={emptyState}
                        alwaysShowToolbar={true}
                        isLoading={isSearching}
                        isError={false}
                        isFiltered={isFiltered()}
                        isEmpty={isFiltered() && results.count === 0}
                    >
                        <ExploreGroupList isFiltered={isFiltered()} groups={(results as GroupSearchResults).groups!} />
                    </ListWithToolbar>
                </PageSection>
            </PageDataLoader>
            <InvalidContentModal
                error={invalidContentError}
                isOpen={isInvalidContentModalOpen}
                onClose={closeInvalidContentModal} />
            <CreateGroupModal
                isOpen={isCreateGroupModalOpen}
                onClose={() => setCreateGroupModalOpen(false)}
                onCreate={doCreateGroup} />
            <ImportModal
                isOpen={isImportModalOpen}
                onClose={onImportModalClose}
                onImport={doImport} />
            <PleaseWaitModal
                message={pleaseWaitMessage}
                isOpen={isPleaseWaitModalOpen} />
            <ProgressModal message="Importing"
                title="Import from .ZIP"
                isCloseable={true}
                progress={importProgress}
                onClose={() => setImporting(false)}
                isOpen={isImporting} />
        </PageErrorHandler>
    );

};
