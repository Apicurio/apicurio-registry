import { FunctionComponent, useEffect, useState } from "react";
import "./ExplorePage.css";
import {
    Button,
    FileUpload,
    Flex,
    FlexItem,
    Form,
    FormGroup,
    FormHelperText,
    HelperText,
    HelperTextItem,
    Modal,
    PageSection,
    PageSectionVariants,
    Spinner
} from "@patternfly/react-core";
import {
    ArtifactList,
    ExplorePageEmptyState,
    ExplorePageToolbar, ExplorePageToolbarFilterCriteria,
    PageDataLoader,
    PageError,
    PageErrorHandler,
    toPageError,
    UploadArtifactForm
} from "@app/pages";
import { InvalidContentModal, RootPageHeader } from "@app/components";
import { ApiError } from "@models/apiError.model.ts";
import { SearchedArtifact } from "@models/searchedArtifact.model.ts";
import { useSearchParams } from "react-router-dom";
import { If, PleaseWaitModal, ProgressModal } from "@apicurio/common-ui-components";
import {
    CreateArtifactData,
    GetArtifactsCriteria,
    Paging,
    useGroupsService
} from "@services/useGroupsService.ts";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { useAdminService } from "@services/useAdminService.ts";
import { useLoggerService } from "@services/useLoggerService.ts";
import { ExploreType } from "@app/pages/explore/ExploreType.ts";
import { ArtifactSearchResults } from "@models/artifactSearchResults.model.ts";

/**
 * Properties
 */
export type ExplorePageProps = {
    // No properties.
}

const EMPTY_UPLOAD_FORM_DATA: CreateArtifactData = {
    content: undefined, fromURL: undefined, groupId: "", id: null, sha: undefined, type: ""
};
const EMPTY_RESULTS: ArtifactSearchResults = {
    artifacts: [],
    count: 0
};

type HookFunctionWrapper = {
    hook: any;
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
    const [exploreType, setExploreType] = useState(ExploreType.ARTIFACT);
    const [criteria, setCriteria] = useState<ExplorePageToolbarFilterCriteria>({
        filterSelection: "name",
        filterValue: "",
        ascending: true
    });
    const [isCreateArtifactModalOpen, setCreateArtifactModalOpen] = useState<boolean>(false);
    const [isCreateGroupModalOpen, setCreateGroupModalOpen] = useState<boolean>(false);
    const [isImportModalOpen, setImportModalOpen] = useState<boolean>(false);
    const [isUploadFormValid, setUploadFormValid] = useState<boolean>(false);
    const [isImportFormValid, setImportFormValid] = useState<boolean>(false);
    const [isInvalidContentModalOpen, setInvalidContentModalOpen] = useState<boolean>(false);
    const [isPleaseWaitModalOpen, setPleaseWaitModalOpen] = useState<boolean>(false);
    const [isSearching, setSearching] = useState<boolean>(false);
    const [paging, setPaging] = useState<Paging>(DEFAULT_PAGING);
    const [results, setResults] = useState<ArtifactSearchResults>(EMPTY_RESULTS);
    const [uploadFormData, setUploadFormData] = useState<CreateArtifactData>(EMPTY_UPLOAD_FORM_DATA);
    const [invalidContentError, setInvalidContentError] = useState<ApiError>();
    const [importFilename, setImportFilename] = useState<string>("");
    const [importFile, setImportFile] = useState<File>();
    const [isImporting, setImporting] = useState(false);
    const [importProgress, setImportProgress] = useState(0);
    const [filterByGroupHook, setFilterByGroupHook] = useState<HookFunctionWrapper>();

    const appNavigation: AppNavigation = useAppNavigation();
    const admin = useAdminService();
    const groups = useGroupsService();
    const logger = useLoggerService();
    const [ searchParams ] = useSearchParams();

    useEffect(() => {
        if (filterByGroupHook && searchParams.get("group")) {
            filterByGroupHook?.hook(searchParams.get("group"));
        }
    }, [filterByGroupHook]);

    const createLoaders = (): Promise<any> => {
        return search(criteria, paging);
    };

    const onCreateGroup = (): void => {
        setCreateGroupModalOpen(true);
    };

    const onCreateArtifact = (): void => {
        setCreateArtifactModalOpen(true);
    };

    const onImportArtifacts = (): void => {
        setImportModalOpen(true);
    };

    const onExportArtifacts = (): void => {
        admin.exportAs("all-artifacts.zip").then(dref => {
            const link = document.createElement("a");
            link.href = dref.href;
            link.download = "all-artifacts.zip";
            link.click();
        }).catch(error => {
            setPageError(toPageError(error, "Failed to export artifacts"));
        });
    };

    const onCreateArtifactModalClose = (): void => {
        setCreateArtifactModalOpen(false);
    };

    const onImportModalClose = (): void => {
        setImportModalOpen(false);
    };

    const onArtifactsLoaded = (results: ArtifactSearchResults): void => {
        setSearching(false);
        setResults(results);
    };

    const doImport = (): void => {
        setImporting(true);
        setImportProgress(0);
        setImportModalOpen(false);

        // Reset the import dialog.
        setImportFilename("");
        setImportFile(undefined);
        setImportFormValid(false);

        if (importFile != null) {
            admin.importFrom(importFile, (event: any) => {
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

    const doCreateArtifact = (): void => {
        onCreateArtifactModalClose();
        pleaseWait(true);

        if (uploadFormData !== null) {
            const data: CreateArtifactData = {
                ...uploadFormData
            };
            // If no groupId is provided, set it to the "default" group
            if (!uploadFormData.groupId) {
                data.groupId = "default";
            }
            groups.createArtifact(data).then(response => {
                const groupId: string = response.artifact.groupId ? response.artifact.groupId : "default";
                const artifactLocation: string = `/explore/${ encodeURIComponent(groupId) }/${ encodeURIComponent(response.artifact.artifactId) }`;
                logger.info("[ExplorePage] Artifact successfully uploaded.  Redirecting to details: ", artifactLocation);
                appNavigation.navigateTo(artifactLocation);
            }).catch( error => {
                pleaseWait(false);
                if (error && (error.error_code === 400 || error.error_code === 409)) {
                    handleInvalidContentError(error);
                } else {
                    setPageError(toPageError(error, "Error uploading artifact."));
                }
                setUploadFormData(EMPTY_UPLOAD_FORM_DATA);
                setUploadFormValid(false);
            });
        }
    };

    const artifacts = (): SearchedArtifact[] => {
        return results ? results.artifacts : [];
    };

    const artifactsCount = (): number => {
        return results ? results.artifacts.length : 0;
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
        const gac: GetArtifactsCriteria = {
            sortAscending: criteria.ascending,
            type: criteria.filterSelection,
            value: criteria.filterValue
        };
        return groups.getArtifacts(gac, paging).then(results => {
            onArtifactsLoaded(results);
        }).catch(error => {
            setPageError(toPageError(error, "Error searching for artifacts."));
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

    const onUploadFormValid = (isValid: boolean): void => {
        setUploadFormValid(isValid);
    };

    const onUploadFormChange = (data: CreateArtifactData): void => {
        setUploadFormData(data);
    };

    const onImportFileChange = (_event: any, file: File): void => {
        const filename: string = file.name;
        const isValid: boolean = filename.toLowerCase().endsWith(".zip");
        setImportFilename(filename);
        setImportFile(file);
        setImportFormValid(isValid);
    };

    const closeInvalidContentModal = (): void => {
        setInvalidContentModalOpen(false);
    };

    const pleaseWait = (isOpen: boolean): void => {
        setPleaseWaitModalOpen(isOpen);
    };

    const handleInvalidContentError = (error: any): void => {
        logger.info("[ExplorePage] Invalid content error:", error);
        setInvalidContentError(error);
        setInvalidContentModalOpen(true);
    };

    const onGroupClick = (groupId: string): void => {
        logger.info("[ExplorePage] Filtering by group: ", groupId);
        // Hack Alert:  when clicking on a Group in the artifact list, push a new filter state into
        // the toolbar.  This is done via a change-criteria hook function that we set up earlier.
        if (filterByGroupHook) {
            filterByGroupHook.hook(groupId);
        }

        // Also reset paging.
        setPaging(DEFAULT_PAGING);
    };

    const showToolbar = (): boolean => {
        // TODO only show when not loading content?
        return true;
    };

    useEffect(() => {
        if (searchParams.get("group")) {
            setLoaders([]);
        } else {
            setLoaders(createLoaders());
        }
    }, []);

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection className="ps_artifacts-header" variant={PageSectionVariants.light} padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={0} />
                </PageSection>
                <If condition={showToolbar}>
                    <PageSection variant={PageSectionVariants.light} padding={{ default: "noPadding" }}>
                        <ExplorePageToolbar
                            exploreType={exploreType}
                            results={results}
                            criteria={criteria}
                            filterByGroupHook={(hook: any) => setFilterByGroupHook({ hook })}
                            paging={paging}
                            onPerPageSelect={onPerPageSelect}
                            onSetPage={onSetPage}
                            onExploreTypeChange={setExploreType}
                            onUploadArtifact={onCreateArtifact}
                            onExportArtifacts={onExportArtifacts}
                            onImportArtifacts={onImportArtifacts}
                            onCriteriaChange={onFilterCriteriaChange} />
                    </PageSection>
                </If>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    {
                        isSearching ?
                            <Flex>
                                <FlexItem><Spinner size="lg"/></FlexItem>
                                <FlexItem><span>Searching...</span></FlexItem>
                            </Flex>
                            :
                            artifactsCount() === 0 ?
                                <ExplorePageEmptyState
                                    exploreType={exploreType}
                                    onCreateArtifact={onCreateArtifact}
                                    onCreateGroup={onCreateGroup}
                                    onImport={onImportArtifacts}
                                    isFiltered={isFiltered()}/>
                                :
                                <ArtifactList artifacts={artifacts()} onGroupClick={onGroupClick} />
                    }
                </PageSection>
            </PageDataLoader>
            <Modal
                title="Upload Artifact"
                variant="large"
                isOpen={isCreateArtifactModalOpen}
                onClose={onCreateArtifactModalClose}
                className="upload-artifact-modal pf-m-redhat-font"
                actions={[
                    <Button key="upload" variant="primary" data-testid="upload-artifact-modal-btn-upload" onClick={doCreateArtifact} isDisabled={!isUploadFormValid}>Upload</Button>,
                    <Button key="cancel" variant="link" data-testid="upload-artifact-modal-btn-cancel" onClick={onCreateArtifactModalClose}>Cancel</Button>
                ]}
            >
                <UploadArtifactForm onChange={onUploadFormChange} onValid={onUploadFormValid} />
            </Modal>
            <InvalidContentModal error={invalidContentError}
                isOpen={isInvalidContentModalOpen}
                onClose={closeInvalidContentModal} />
            <Modal
                title="Upload multiple artifacts"
                variant="medium"
                isOpen={isImportModalOpen}
                onClose={onImportModalClose}
                className="import-artifacts-modal pf-m-redhat-font"
                actions={[
                    <Button key="upload" variant="primary" data-testid="upload-artifacts-modal-btn-upload" onClick={doImport} isDisabled={!isImportFormValid}>Upload</Button>,
                    <Button key="cancel" variant="link" data-testid="upload-artifacts-modal-btn-cancel" onClick={onImportModalClose}>Cancel</Button>
                ]}
            >
                <Form>
                    <FormGroup isRequired={false} fieldId="form-summary">
                        <p>
                            Select an artifacts .zip file previously downloaded from a Registry instance.
                        </p>
                    </FormGroup>
                    <FormGroup
                        label="ZIP File"
                        isRequired={true}
                        fieldId="form-file"
                    >
                        <FileUpload
                            id="import-content"
                            data-testid="form-import"
                            filename={importFilename}
                            filenamePlaceholder="Drag and drop or choose a .zip file"
                            isRequired={true}
                            onFileInputChange={onImportFileChange}
                        />
                        <FormHelperText>
                            <HelperText>
                                <HelperTextItem>File format must be .zip</HelperTextItem>
                            </HelperText>
                        </FormHelperText>
                    </FormGroup>
                </Form>
            </Modal>
            <PleaseWaitModal message="Creating artifact, please wait..."
                isOpen={isPleaseWaitModalOpen} />
            <ProgressModal message="Importing artifacts"
                title="Upload multiple artifacts"
                isCloseable={true}
                progress={importProgress}
                onClose={() => setImporting(false)}
                isOpen={isImporting} />
        </PageErrorHandler>
    );

};
