import { FunctionComponent, useEffect, useState } from "react";
import "./ArtifactsPage.css";
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
    ArtifactsPageEmptyState,
    ArtifactsPageToolbar,
    ArtifactsPageToolbarFilterCriteria,
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
    ArtifactsSearchResults,
    CreateArtifactData,
    GetArtifactsCriteria,
    Paging,
    useGroupsService
} from "@services/useGroupsService.ts";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { useAdminService } from "@services/useAdminService.ts";
import { useLoggerService } from "@services/useLoggerService.ts";

/**
 * Properties
 */
export type ArtifactsPageProps = {
    // No properties.
}

const EMPTY_UPLOAD_FORM_DATA: CreateArtifactData = {
    content: undefined, fromURL: undefined, groupId: "", id: null, sha: undefined, type: ""
};
const EMPTY_RESULTS: ArtifactsSearchResults = {
    artifacts: [],
    count: 0,
    page: 1,
    pageSize: 10
};

type HookFunctionWrapper = {
    hook: any;
};

const DEFAULT_PAGING: Paging = {
    page: 1,
    pageSize: 10
};

/**
 * The artifacts page.
 */
//class ArtifactsPageInternal extends PageComponent<ArtifactsPageInternalProps, ArtifactsPageState> {
export const ArtifactsPage: FunctionComponent<ArtifactsPageProps> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [criteria, setCriteria] = useState<ArtifactsPageToolbarFilterCriteria>({
        filterSelection: "name",
        filterValue: "",
        ascending: true
    });
    const [isUploadModalOpen, setUploadModalOpen] = useState<boolean>(false);
    const [isImportModalOpen, setImportModalOpen] = useState<boolean>(false);
    const [isUploadFormValid, setUploadFormValid] = useState<boolean>(false);
    const [isImportFormValid, setImportFormValid] = useState<boolean>(false);
    const [isInvalidContentModalOpen, setInvalidContentModalOpen] = useState<boolean>(false);
    const [isPleaseWaitModalOpen, setPleaseWaitModalOpen] = useState<boolean>(false);
    const [isSearching, setSearching] = useState<boolean>(false);
    const [paging, setPaging] = useState<Paging>(DEFAULT_PAGING);
    const [results, setResults] = useState<ArtifactsSearchResults>(EMPTY_RESULTS);
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

    const onUploadArtifact = (): void => {
        setUploadModalOpen(true);
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

    const onUploadModalClose = (): void => {
        setUploadModalOpen(false);
    };

    const onImportModalClose = (): void => {
        setImportModalOpen(false);
    };

    const onArtifactsLoaded = (results: ArtifactsSearchResults): void => {
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

    const doUploadArtifact = (): void => {
        onUploadModalClose();
        pleaseWait(true);

        if (uploadFormData !== null) {
            const data: CreateArtifactData = {
                ...uploadFormData
            };
            // If no groupId is provided, set it to the "default" group
            if (!uploadFormData.groupId) {
                data.groupId = "default";
            }
            groups.createArtifact(data).then(metaData => {
                const groupId: string = metaData.groupId ? metaData.groupId : "default";
                const artifactLocation: string = `/artifacts/${ encodeURIComponent(groupId) }/${ encodeURIComponent(metaData.id) }`;
                logger.info("[ArtifactsPage] Artifact successfully uploaded.  Redirecting to details: ", artifactLocation);
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

    const onFilterCriteriaChange = (newCriteria: ArtifactsPageToolbarFilterCriteria): void => {
        setCriteria(newCriteria);
        search(newCriteria, paging);
    };

    const isFiltered = (): boolean => {
        return !!criteria.filterValue;
    };

    const search = async (criteria: ArtifactsPageToolbarFilterCriteria, paging: Paging): Promise<any> => {
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
        logger.info("[ArtifactsPage] Invalid content error:", error);
        setInvalidContentError(error);
        setInvalidContentModalOpen(true);
    };

    const onGroupClick = (groupId: string): void => {
        logger.info("[ArtifactsPage] Filtering by group: ", groupId);
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
                        <ArtifactsPageToolbar
                            artifacts={results}
                            criteria={criteria}
                            filterByGroupHook={(hook: any) => setFilterByGroupHook({ hook })}
                            paging={paging}
                            onPerPageSelect={onPerPageSelect}
                            onSetPage={onSetPage}
                            onUploadArtifact={onUploadArtifact}
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
                                <ArtifactsPageEmptyState onUploadArtifact={onUploadArtifact}
                                    onImportArtifacts={onImportArtifacts}
                                    isFiltered={isFiltered()}/>
                                :
                                <ArtifactList artifacts={artifacts()} onGroupClick={onGroupClick} />
                    }
                </PageSection>
            </PageDataLoader>
            <Modal
                title="Upload Artifact"
                variant="large"
                isOpen={isUploadModalOpen}
                onClose={onUploadModalClose}
                className="upload-artifact-modal pf-m-redhat-font"
                actions={[
                    <Button key="upload" variant="primary" data-testid="upload-artifact-modal-btn-upload" onClick={doUploadArtifact} isDisabled={!isUploadFormValid}>Upload</Button>,
                    <Button key="cancel" variant="link" data-testid="upload-artifact-modal-btn-cancel" onClick={onUploadModalClose}>Cancel</Button>
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
