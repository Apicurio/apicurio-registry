import { CSSProperties, FunctionComponent, useEffect, useRef, useState } from "react";
import "./EditorPage.css";
import {
    EmptyState, EmptyStateBody,
    EmptyStateVariant,
    PageSection,
    useInterval
} from "@patternfly/react-core";
import { useParams } from "react-router";
import {
    CompareModal,
    DraftRecoveryModal,
    EditorContext,
    EXPLORE_PAGE_IDX,
    PageDataLoader,
    PageError,
    PageErrorHandler,
    PageProperties,
    ReauthenticateModal,
    toPageError
} from "@app/pages";
import { RootPageHeader } from "@app/components";
import { ContentTypes } from "@models/ContentTypes.ts";
import { AuthService, PleaseWaitModal, useAuth } from "@apicurio/common-ui-components";
import { Draft, DraftContent } from "@models/drafts";
import { useDraftsService } from "@services/useDraftsService.ts";
import { useDownloadService } from "@services/useDownloadService.ts";
import { isStringEmptyOrUndefined } from "@utils/string.utils.ts";
import {
    contentTypeForDraft,
    convertToValidFilename,
    fileExtensionForDraft,
    formatContent
} from "@utils/content.utils.ts";
import { ConfirmOverwriteModal } from "@app/pages/drafts/components/modals";
import { WarningTriangleIcon } from "@patternfly/react-icons";
import { TextEditor } from "@editors/TextEditor.tsx";
import { ProtoEditor } from "@editors/ProtoEditor.tsx";
import { OpenApiEditor } from "@editors/OpenApiEditor.tsx";
import { AsyncApiEditor } from "@editors/AsyncApiEditor.tsx";
import { ArtifactTypes } from "@services/useArtifactTypesService.ts";
import { useLoggerService } from "@services/useLoggerService.ts";
import { LocalStorageService, useLocalStorageService } from "@services/useLocalStorageService.ts";
import { ReauthenticationService, useReauthenticationService } from "@services/useReauthenticationService.ts";
import { isErrorStatus } from "@utils/rest.utils.ts";
import {
    createEditorDraftSnapshot,
    createEditorDraftSnapshotKey,
    EditorDraftSnapshot,
    serializeEditorDraftContent
} from "./editorDraftSnapshot.ts";

const sectionContextStyle: CSSProperties = {
    borderBottom: "1px solid #ccc",
    marginBottom: "1px",
    padding: "12px 12px 12px 24px"
};
const sectionEditorStyle: CSSProperties = {
    padding: 0,
    display: "flex",
    flexFlow: "column",
    height: "auto",
    width: "100%"
};
const editorParentStyle: CSSProperties = {
    flexGrow: 1
};

type EditorContent = string | object | undefined;
type SnapshotPersistenceResult = "saved" | "cleared" | "skipped" | "quota_exceeded";
type SnapshotContext = {
    content: EditorContent;
    isDirty: boolean;
    isDraftLoaded: boolean;
    isDraftContentLoaded: boolean;
    isDraftRecoveryModalOpen: boolean;
    groupId: string;
    draftId: string;
    version: string;
    contentType: string;
    snapshotKey: string;
};

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
    const [originalContent, setOriginalContent] = useState<EditorContent>();
    const [currentContent, setCurrentContent] = useState<EditorContent>();
    const [isDirty, setDirty] = useState(false);
    const [isCompareModalOpen, setCompareModalOpen] = useState(false);
    const [isPleaseWaitModalOpen, setPleaseWaitModalOpen] = useState(false);
    const [isConfirmOverwriteModalOpen, setConfirmOverwriteModalOpen] = useState(false);
    const [isDraftRecoveryModalOpen, setDraftRecoveryModalOpen] = useState(false);
    const [isReauthenticateModalOpen, setReauthenticateModalOpen] = useState(false);
    const [isReauthenticateRedirecting, setReauthenticateRedirecting] = useState(false);
    const [didReauthenticateSnapshotSaveFail, setReauthenticateSnapshotSaveFail] = useState(false);
    const [pleaseWaitMessage, setPleaseWaitMessage] = useState("");
    const [isContentConflicting, setIsContentConflicting] = useState(false);
    const [draftRecoverySnapshot, setDraftRecoverySnapshot] = useState<EditorDraftSnapshot | undefined>();
    const [isDraftLoaded, setDraftLoaded] = useState(false);
    const [isDraftContentLoaded, setDraftContentLoaded] = useState(false);
    const [iframeEditorKey, setIframeEditorKey] = useState(0);

    const { groupId, artifactId, version } = useParams();
    const routeGroupId = groupId as string;
    const draftId: string = artifactId || "";
    const routeVersion = version as string;
    const draftDisplayName = draft.name || draft.draftId;

    const drafts = useDraftsService();
    const downloadSvc = useDownloadService();
    const logger = useLoggerService();
    const auth: AuthService = useAuth();
    const localStorage: LocalStorageService = useLocalStorageService();
    const reauthentication: ReauthenticationService = useReauthenticationService();
    const checkedSnapshotKeyRef = useRef<string | undefined>(undefined);
    const recoveryDecisionPendingRef = useRef(false);
    const isAuthRedirectInProgressRef = useRef(false);

    const snapshotKey = createEditorDraftSnapshotKey(routeGroupId, draftId, routeVersion, draftContent.contentType);
    const snapshotContextRef = useRef<SnapshotContext>({
        content: currentContent,
        isDirty,
        isDraftLoaded,
        isDraftContentLoaded,
        isDraftRecoveryModalOpen,
        groupId: routeGroupId,
        draftId,
        version: routeVersion,
        contentType: draftContent.contentType,
        snapshotKey
    });

    const applyEditorContent = (content: string): void => {
        setDraftContent(previous => ({
            ...previous,
            content
        }));
        setCurrentContent(content);
    };

    const clearDraftRecoverySnapshot = (): void => {
        localStorage.clearSnapshot(snapshotKey);
    };

    const resetDraftRecoveryState = (): void => {
        recoveryDecisionPendingRef.current = false;
        setDraftRecoverySnapshot(undefined);
        setDraftRecoveryModalOpen(false);
    };

    const syncSavedContent = (content: string): void => {
        setDraftContent(previous => ({
            ...previous,
            content
        }));
        setOriginalContent(content);
        setCurrentContent(content);
        setDirty(false);
    };

    const openReauthenticateModal = (): void => {
        isAuthRedirectInProgressRef.current = false;
        setPleaseWaitModalOpen(false);
        setReauthenticateRedirecting(false);
        setReauthenticateSnapshotSaveFail(false);
        setReauthenticateModalOpen(true);
    };

    const requestReauthenticationIfUnauthorized = async (error: unknown): Promise<boolean> => {
        if (!isErrorStatus(error, 401)) {
            return false;
        }

        return reauthentication.requestReauthentication(auth);
    };

    const handleRequestError = async (
        error: unknown,
        onUnhandled: () => void
    ): Promise<void> => {
        if (!await requestReauthenticationIfUnauthorized(error)) {
            onUnhandled();
        }
    };

    const persistDraftRecoverySnapshot = (): SnapshotPersistenceResult => {
        const snapshotContext = snapshotContextRef.current;

        // Do not offer recovery while the page is still loading or while the recovery dialog is already active.
        if (!snapshotContext.isDraftLoaded || !snapshotContext.isDraftContentLoaded || snapshotContext.isDraftRecoveryModalOpen) {
            return "skipped";
        }

        if (!snapshotContext.isDirty) {
            localStorage.clearSnapshot(snapshotContext.snapshotKey);
            return "cleared";
        }

        const snapshot = createEditorDraftSnapshot({
            groupId: snapshotContext.groupId,
            draftId: snapshotContext.draftId,
            version: snapshotContext.version,
            contentType: snapshotContext.contentType,
            content: serializeEditorDraftContent(snapshotContext.content)
        });

        if (!localStorage.storeSnapshot(snapshotContext.snapshotKey, snapshot)) {
            logger.warn("Unable to persist draft recovery snapshot because browser storage is full.");
            return "quota_exceeded";
        }

        return "saved";
    };

    const loadDraft = async (): Promise<void> => {
        try {
            const loadedDraft = await drafts.getDraft(routeGroupId, draftId, routeVersion);
            setDraft(loadedDraft);
            setDraftLoaded(true);
        } catch (error) {
            setPageError(toPageError(error, "Error loading page data."));
            await requestReauthenticationIfUnauthorized(error);
        }
    };

    const loadDraftContent = async (): Promise<void> => {
        try {
            const content = await drafts.getDraftContent(routeGroupId, draftId, routeVersion);
            setOriginalContent(content.content);
            setCurrentContent(content.content);
            setDraftContent(content);
            setDraftContentLoaded(true);
        } catch (error) {
            setPageError(toPageError(error, "Error loading page data."));
            await requestReauthenticationIfUnauthorized(error);
        }
    };

    const createLoaders = (): Promise<any>[] => {
        return [
            loadDraft(),
            loadDraftContent(),
        ];
    };

    useEffect(() => {
        snapshotContextRef.current = {
            content: currentContent,
            isDirty,
            isDraftLoaded,
            isDraftContentLoaded,
            isDraftRecoveryModalOpen,
            groupId: routeGroupId,
            draftId,
            version: routeVersion,
            contentType: draftContent.contentType,
            snapshotKey
        };
    }, [
        currentContent,
        draftContent.contentType,
        draftId,
        isDirty,
        isDraftContentLoaded,
        isDraftLoaded,
        isDraftRecoveryModalOpen,
        routeGroupId,
        routeVersion,
        snapshotKey
    ]);

    useEffect(() => {
        // Route changes must invalidate the previous draft load state before recovery checks run again.
        setDraftLoaded(false);
        setDraftContentLoaded(false);
        setDraftRecoverySnapshot(undefined);
        setDraftRecoveryModalOpen(false);
        setReauthenticateModalOpen(false);
        setPageError(undefined);
        setIsContentConflicting(false);
        isAuthRedirectInProgressRef.current = false;
        recoveryDecisionPendingRef.current = false;
        checkedSnapshotKeyRef.current = undefined;
        setLoaders(createLoaders());
    }, [draftId, routeGroupId, routeVersion]);

    useEffect(() => {
        return reauthentication.registerReauthenticationInterceptor(async () => {
            // The editor intercepts the global re-auth flow so it can save a local recovery snapshot first.
            openReauthenticateModal();
            return true;
        });
    }, [reauthentication]);

    // Add browser hook to prevent navigation and tab closing when the editor is dirty
    useEffect(() => {
        const handleBeforeUnload = (e: BeforeUnloadEvent): void => {
            // Skip the browser prompt for the intentional auth redirect started from the re-auth modal.
            if (isAuthRedirectInProgressRef.current) {
                return;
            }

            persistDraftRecoverySnapshot();
            e.preventDefault();
        };

        if (isDirty) {
            window.addEventListener("beforeunload", handleBeforeUnload);
            return () => {
                window.removeEventListener("beforeunload", handleBeforeUnload);
            };
        }
    }, [isDirty]);

    useEffect(() => {
        setDirty(originalContent !== currentContent);
    }, [currentContent, originalContent]);

    useEffect(() => {
        // Recovery is offered only after both metadata and server content are loaded for the current draft snapshot key.
        if (!isDraftLoaded || !isDraftContentLoaded) {
            return;
        }
        // Avoid reopening the same recovery modal after local state updates for the same draft/version/content type.
        if (checkedSnapshotKeyRef.current === snapshotKey) {
            return;
        }

        checkedSnapshotKeyRef.current = snapshotKey;

        const storedSnapshot = localStorage.loadSnapshot<EditorDraftSnapshot>(snapshotKey);
        if (!storedSnapshot) {
            recoveryDecisionPendingRef.current = false;
            return;
        }

        const serverContent = serializeEditorDraftContent(originalContent);
        if (storedSnapshot.content === serverContent) {
            recoveryDecisionPendingRef.current = false;
            clearDraftRecoverySnapshot();
            return;
        }

        recoveryDecisionPendingRef.current = true;
        setDraftRecoverySnapshot(storedSnapshot);
        setDraftRecoveryModalOpen(true);
    }, [isDraftContentLoaded, isDraftLoaded, localStorage, originalContent, snapshotKey]);

    useEffect(() => {
        // Once the draft is back in sync with the server and no recovery choice is pending, the local snapshot is stale.
        if (isDraftLoaded && isDraftContentLoaded && !isDraftRecoveryModalOpen && !recoveryDecisionPendingRef.current && !isDirty) {
            localStorage.clearSnapshot(snapshotKey);
        }
    }, [isDirty, isDraftContentLoaded, isDraftLoaded, isDraftRecoveryModalOpen, localStorage, snapshotKey]);

    // Poll the server for new content every 30s.  If the content has been updated on
    // the server then we have a conflict that we need to report to the user.
    useInterval(() => {
        void detectContentConflict();
    }, 30000);


    const pleaseWait = (message: string = ""): void => {
        setPleaseWaitModalOpen(true);
        setPleaseWaitMessage(message);
    };

    const detectContentConflict = async (): Promise<void> => {
        try {
            const currentDraft = await drafts.getDraft(routeGroupId, draftId, routeVersion);
            console.info(`[EditorPage] Detecting conflicting content.  Latest contentId: ${currentDraft.contentId}  Editor contentId: ${draft.contentId}`);
            if (currentDraft.contentId !== draft.contentId) {
                console.debug(`[EditorPage] Detected Draft content conflict.  Expected '${draft.contentId}' but found '${currentDraft.contentId}'`);
                setIsContentConflicting(true);
            }
        } catch (error) {
            await handleRequestError(error, () => {
                logger.error(error);
            });
        }
    };

    const updateDraftMetadata = async (): Promise<void> => {
        try {
            setDraft(await drafts.getDraft(routeGroupId, draftId, routeVersion));
        } catch (error) {
            await handleRequestError(error, () => {
                logger.error(error);
            });
        }
    };

    const saveDraftContent = async (contentToSave: string): Promise<void> => {
        try {
            await drafts.updateDraftContent(routeGroupId, draftId, routeVersion, {
                content: contentToSave,
                contentType: draftContent.contentType
            });
            setPleaseWaitModalOpen(false);
            clearDraftRecoverySnapshot();
            void updateDraftMetadata();
            syncSavedContent(contentToSave);
            resetDraftRecoveryState();
        } catch (error) {
            setPleaseWaitModalOpen(false);
            await handleRequestError(error, () => {
                console.error("[EditorPage] Failed to save design content: ", error);
            });
        }
    };

    const checkContentConflictAndSave = async (contentToSave: string): Promise<void> => {
        console.debug("[EditorPage] Checking for conflicting Draft content");
        try {
            const currentDraft = await drafts.getDraft(routeGroupId, draftId, routeVersion);
            if (currentDraft.contentId !== draft.contentId) {
                console.debug(`[EditorPage] Detected Draft content conflict.  Expected '${draft.contentId}' but found '${currentDraft.contentId}'.'`);
                // Uh oh, if we save now we'll be overwriting someone else's changes!
                setPleaseWaitModalOpen(false);
                setConfirmOverwriteModalOpen(true);
                return;
            }

            console.debug("[EditorPage] Draft content not in conflict, saving...");
            await saveDraftContent(contentToSave);
        } catch (error) {
            setPleaseWaitModalOpen(false);
            await handleRequestError(error, () => {
                console.error("[EditorPage] Failed to save design content: ", error);
            });
        }
    };

    // Called when the user makes an edit in the editor.  First checks that the
    // content hasn't been changed on the server by someone else...
    const onSave = async (overwrite: boolean = false): Promise<void> => {
        pleaseWait("Saving the draft, please wait...");
        const contentToSave = serializeEditorDraftContent(currentContent);

        if (overwrite) {
            await saveDraftContent(contentToSave);
            return;
        }

        await checkContentConflictAndSave(contentToSave);
    };

    const onFormat = (): void => {
        console.info("[EditorPage] Formatting content.");
        const formattedContent: string = formatContent(serializeEditorDraftContent(currentContent), draftContent.contentType);
        console.info("[EditorPage] New content is: ", formattedContent);
        applyEditorContent(formattedContent);
    };

    const onDownload = (): void => {
        if (draft) {
            const fname: string = isStringEmptyOrUndefined(draft.name) ? draft.draftId : draft.name;
            const filename: string = `${convertToValidFilename(fname)}.${fileExtensionForDraft(draft, draftContent)}`;
            const contentType: string = contentTypeForDraft(draft, draftContent);
            const theContent: string = typeof currentContent === "object" ? JSON.stringify(currentContent, null, 4) : currentContent as string;
            downloadSvc.downloadToFS(theContent, contentType, filename);
        }
    };

    // Called when the user makes an edit in the editor.
    const onEditorChange = (value: EditorContent): void => {
        setCurrentContent(value);
    };

    const onCompareContent = () => {
        setCompareModalOpen(true);
    };

    const closeCompareEditor = () => {
        setCompareModalOpen(false);
    };

    const onRestoreDraftRecovery = (): void => {
        if (!draftRecoverySnapshot) {
            return;
        }

        applyEditorContent(draftRecoverySnapshot.content);
        // The iframe-based editors only consume their initial content during bootstrap,
        // so restoring local changes must remount them to rehydrate their internal state.
        setIframeEditorKey(previous => previous + 1);
        resetDraftRecoveryState();
    };

    const onDiscardDraftRecovery = (): void => {
        clearDraftRecoverySnapshot();
        resetDraftRecoveryState();
    };

    const onReauthenticate = async (): Promise<void> => {
        try {
            setReauthenticateRedirecting(true);
            // Suppress the regular beforeunload prompt only for this explicit auth redirect.
            isAuthRedirectInProgressRef.current = true;
            // After the quota warning is shown once, let the follow-up confirmation continue without retrying the same failed snapshot write.
            if (!didReauthenticateSnapshotSaveFail) {
                const persistenceResult = persistDraftRecoverySnapshot();
                if (persistenceResult === "quota_exceeded") {
                    setReauthenticateSnapshotSaveFail(true);
                    setReauthenticateRedirecting(false);
                    isAuthRedirectInProgressRef.current = false;
                    return;
                }
            }
            await reauthentication.startReauthenticationRedirect(auth);
        } catch (error) {
            console.error("[EditorPage] Failed to initiate re-authentication redirect", error);
            setReauthenticateRedirecting(false);
            isAuthRedirectInProgressRef.current = false;
        }
    };

    const onCloseReauthenticateModal = (): void => {
        if (!isReauthenticateRedirecting) {
            isAuthRedirectInProgressRef.current = false;
            setReauthenticateRedirecting(false);
            setReauthenticateSnapshotSaveFail(false);
            reauthentication.cancelReauthentication();
            setReauthenticateModalOpen(false);
        }
    };

    const notDraftEmptyState = (
        <EmptyState titleText="Not a Draft" icon={WarningTriangleIcon} variant={EmptyStateVariant.sm}>
            <EmptyStateBody>
                This artifact is not in <em>DRAFT</em> status and so its content cannot be edited.
            </EmptyStateBody>
        </EmptyState>
    );

    const textEditor: React.ReactElement = (
        <TextEditor content={draftContent} onChange={onEditorChange}/>
    );

    const protoEditor: React.ReactElement = (
        <ProtoEditor content={draftContent} onChange={onEditorChange}/>
    );

    const openapiEditor: React.ReactElement = (
        <OpenApiEditor key={`openapi-${snapshotKey}-${iframeEditorKey}`} content={draftContent} onChange={onEditorChange}/>
    );

    const asyncapiEditor: React.ReactElement = (
        <AsyncApiEditor key={`asyncapi-${snapshotKey}-${iframeEditorKey}`} content={draftContent} onChange={onEditorChange}/>
    );

    const editor = (): React.ReactElement => {
        if (isDraftLoaded && draft.isDraft === false) {
            logger.warn("Artifact version is not a draft.");
            return notDraftEmptyState;
        }

        if (draft?.type === ArtifactTypes.OPENAPI) {
            logger.info("Draft is of type OPENAPI");
            return openapiEditor;
        } else if (draft?.type === ArtifactTypes.ASYNCAPI) {
            logger.info("Draft is of type ASYNCAPI");
            return asyncapiEditor;
        } else if (draft?.type === ArtifactTypes.PROTOBUF) {
            logger.info("Draft is of type PROTOBUF");
            return protoEditor;
        }

        // TODO create different text editors depending on the content type?  Or assume
        // that the text editor can configure itself appropriately?
        return textEditor;
    };

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection hasBodyWrapper={false} className="ps_explore-header"  padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={EXPLORE_PAGE_IDX} />
                </PageSection>
                <PageSection hasBodyWrapper={false}  id="section-context" style={sectionContextStyle}>
                    <EditorContext
                        draft={draft}
                        dirty={isDirty}
                        contentConflict={isContentConflicting}
                        onSave={onSave}
                        onFormat={onFormat}
                        onDownload={onDownload}
                        onCompareContent={onCompareContent}
                    />
                </PageSection>
                <PageSection hasBodyWrapper={false}  id="section-editor" style={sectionEditorStyle} isFilled={true}>
                    <div className="editor-parent" style={editorParentStyle} children={editor() as any} />
                </PageSection>
            </PageDataLoader>
            <CompareModal isOpen={isCompareModalOpen}
                onClose={closeCompareEditor}
                before={originalContent}
                beforeName={draftDisplayName}
                after={currentContent}
                afterName={draftDisplayName}/>
            <ConfirmOverwriteModal
                isOpen={isConfirmOverwriteModalOpen}
                onOverwrite={() => {
                    setConfirmOverwriteModalOpen(false);
                    onSave(true);
                }}
                onClose={() => setConfirmOverwriteModalOpen(false)} />
            <DraftRecoveryModal
                isOpen={isDraftRecoveryModalOpen}
                draftName={draftDisplayName}
                draftVersion={draft.version}
                savedOn={draftRecoverySnapshot?.savedOn}
                onRestore={onRestoreDraftRecovery}
                onDiscard={onDiscardDraftRecovery} />
            <ReauthenticateModal
                isOpen={isReauthenticateModalOpen}
                isRedirecting={isReauthenticateRedirecting}
                snapshotSaveFailed={didReauthenticateSnapshotSaveFail}
                onConfirm={onReauthenticate}
                onClose={onCloseReauthenticateModal} />
            <PleaseWaitModal message={pleaseWaitMessage}
                isOpen={isPleaseWaitModalOpen} />
        </PageErrorHandler>
    );

};
