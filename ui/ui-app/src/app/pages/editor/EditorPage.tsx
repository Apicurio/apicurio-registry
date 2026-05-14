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
import { PleaseWaitModal } from "@apicurio/common-ui-components";
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
import { serializeEditorDraftContent } from "./editorDraftSnapshot.ts";
import { useEditorDraftRecovery } from "./useEditorDraftRecovery.ts";
import { useEditorReauthentication } from "./useEditorReauthentication.ts";

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
    const [pleaseWaitMessage, setPleaseWaitMessage] = useState("");
    const [isContentConflicting, setIsContentConflicting] = useState(false);
    const [isDraftLoaded, setDraftLoaded] = useState(false);
    const [isDraftContentLoaded, setDraftContentLoaded] = useState(false);

    const params = useParams();
    const groupId = params.groupId as string;
    const draftId: string = params.artifactId || "";
    const version = params.version as string;
    const draftDisplayName = draft.name || draft.draftId;

    const drafts = useDraftsService();
    const downloadSvc = useDownloadService();
    const logger = useLoggerService();

    const applyEditorContent = (content: string): void => {
        setDraftContent(previous => ({
            ...previous,
            content
        }));
        setCurrentContent(content);
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

    const isAuthRedirectInProgressRef = useRef(false);
    const {
        snapshotKey,
        iframeEditorKey,
        draftRecoverySnapshot,
        isDraftRecoveryModalOpen,
        persistDraftRecoverySnapshot,
        onRestoreDraftRecovery,
        onDiscardDraftRecovery,
        onDraftSaved
    } = useEditorDraftRecovery({
        groupId,
        draftId,
        version,
        contentType: draftContent.contentType,
        currentContent,
        originalContent,
        isDirty,
        isDraftLoaded,
        isDraftContentLoaded,
        isAuthRedirectInProgressRef,
        applyEditorContent
    });

    const {
        isReauthenticateModalOpen,
        isReauthenticateRedirecting,
        confirmWithoutSnapshot,
        requestReauthenticationIfUnauthorized,
        handleRequestError,
        onReauthenticate,
        onCloseReauthenticateModal
    } = useEditorReauthentication({
        groupId,
        draftId,
        version,
        isAuthRedirectInProgressRef,
        persistDraftRecoverySnapshot
    });

    const loadDraft = async (): Promise<void> => {
        try {
            const loadedDraft = await drafts.getDraft(groupId, draftId, version);
            setDraft(loadedDraft);
            setDraftLoaded(true);
        } catch (error) {
            setPageError(toPageError(error, "Error loading page data."));
            await requestReauthenticationIfUnauthorized(error);
        }
    };

    const loadDraftContent = async (): Promise<void> => {
        try {
            const content = await drafts.getDraftContent(groupId, draftId, version);
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
        // Route changes must invalidate the previous draft load state before recovery checks run again.
        setDraftLoaded(false);
        setDraftContentLoaded(false);
        setPleaseWaitModalOpen(false);
        setPageError(undefined);
        setIsContentConflicting(false);
        isAuthRedirectInProgressRef.current = false;
        setLoaders(createLoaders());
    }, [draftId, groupId, version]);

    useEffect(() => {
        setDirty(originalContent !== currentContent);
    }, [currentContent, originalContent]);

    useEffect(() => {
        // Re-authentication replaces the active save flow, so any stale loading overlay should be dismissed.
        if (isReauthenticateModalOpen) {
            setPleaseWaitModalOpen(false);
        }
    }, [isReauthenticateModalOpen]);

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
            const currentDraft = await drafts.getDraft(groupId, draftId, version);
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
            setDraft(await drafts.getDraft(groupId, draftId, version));
        } catch (error) {
            await handleRequestError(error, () => {
                logger.error(error);
            });
        }
    };

    const saveDraftContent = async (contentToSave: string): Promise<void> => {
        try {
            await drafts.updateDraftContent(groupId, draftId, version, {
                content: contentToSave,
                contentType: draftContent.contentType
            });
            setPleaseWaitModalOpen(false);
            void updateDraftMetadata();
            syncSavedContent(contentToSave);
            onDraftSaved();
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
            const currentDraft = await drafts.getDraft(groupId, draftId, version);
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
                confirmWithoutSnapshot={confirmWithoutSnapshot}
                onConfirm={onReauthenticate}
                onClose={onCloseReauthenticateModal} />
            <PleaseWaitModal message={pleaseWaitMessage}
                isOpen={isPleaseWaitModalOpen} />
        </PageErrorHandler>
    );

};
