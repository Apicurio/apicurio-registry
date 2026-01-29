import { CSSProperties, FunctionComponent, useEffect, useState } from "react";
import "./EditorPage.css";
import {
    EmptyState, EmptyStateBody,
    EmptyStateVariant,
    PageSection,
    Title, useInterval
} from "@patternfly/react-core";
import { useParams } from "react-router-dom";
import {
    CompareModal,
    EditorContext,
    EXPLORE_PAGE_IDX,
    PageDataLoader,
    PageError,
    PageErrorHandler,
    PageProperties,
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

// Event listener used to prevent navigation when the editor is dirty
const onBeforeUnload = (e: Event): void => {
    // Cancel the event
    e.preventDefault();
    // Chrome requires returnValue to be set
    e.returnValue = true;
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
    const [originalContent, setOriginalContent] = useState<any>();
    const [currentContent, setCurrentContent] = useState<any>();
    const [isDirty, setDirty] = useState(false);
    const [isCompareModalOpen, setCompareModalOpen] = useState(false);
    const [isPleaseWaitModalOpen, setPleaseWaitModalOpen] = useState(false);
    const [isConfirmOverwriteModalOpen, setConfirmOverwriteModalOpen] = useState(false);
    const [pleaseWaitMessage, setPleaseWaitMessage] = useState("");
    const [isContentConflicting, setIsContentConflicting] = useState(false);

    const { groupId, artifactId, version } = useParams();
    const draftId: string = artifactId || "";

    const drafts = useDraftsService();
    const downloadSvc = useDownloadService();
    const logger = useLoggerService();

    const createLoaders = (): Promise<any>[] => {
        return [
            drafts.getDraft(groupId as string, draftId as string, version as string)
                .then(d => {
                    setDraft(d);
                })
                .catch(error => {
                    setPageError(toPageError(error, "Error loading page data."));
                }),
            drafts.getDraftContent(groupId as string, draftId as string, version as string).then(content => {
                setOriginalContent(content.content);
                setCurrentContent(content.content);
                setDraftContent(content);
            }),
        ];
    };

    useEffect(() => {
        setLoaders(createLoaders());
        // Cleanup any possible event listener we might still have registered
        return () => {
            window.removeEventListener("beforeunload", onBeforeUnload);
        };
    }, [groupId, draftId, version]);

    // Add browser hook to prevent navigation and tab closing when the editor is dirty
    useEffect(() => {
        if (isDirty) {
            window.addEventListener("beforeunload", onBeforeUnload);
        } else {
            window.removeEventListener("beforeunload", onBeforeUnload);
        }
    }, [isDirty]);

    useEffect(() => {
        setDirty(originalContent != currentContent);
    }, [currentContent]);

    // Poll the server for new content every 60s.  If the content has been updated on
    // the server then we have a conflict that we need to report to the user.
    useInterval(() => {
        detectContentConflict();
    }, 30000);


    const pleaseWait = (message: string = ""): void => {
        setPleaseWaitModalOpen(true);
        setPleaseWaitMessage(message);
    };

    const detectContentConflict = (): void => {
        drafts.getDraft(groupId as string, draftId as string, version as string).then(currentDraft => {
            console.info(`[EditorPage] Detecting conflicting content.  Latest contentId: ${currentDraft.contentId}  Editor contentId: ${draft.contentId}`);
            if (currentDraft.contentId !== draft.contentId) {
                console.debug(`[EditorPage] Detected Draft content conflict.  Expected '${draft.contentId}' but found '${currentDraft.contentId}'`);
                setIsContentConflicting(true);
            }
        });
    };

    const updateDraftMetadata = (): void => {
        drafts.getDraft(groupId as string, draftId as string, version as string).then(setDraft);
    };

    // Called when the user makes an edit in the editor.  First checks that the
    // content hasn't been changed on the server by someone else...
    const onSave = (overwrite?: boolean): Promise<void> => {
        pleaseWait("Saving the draft, please wait...");

        if (overwrite === undefined) {
            overwrite = false;
        }

        if (overwrite) {
            const content: DraftContent = {
                content: currentContent,
                contentType: draftContent.contentType
            };
            return drafts.updateDraftContent(groupId as string, draftId as string, version as string, content).then(() => {
                setPleaseWaitModalOpen(false);
                if (draft) {
                    updateDraftMetadata();
                    setOriginalContent(currentContent);
                    setDirty(false);
                }
            }).catch(error => {
                setPleaseWaitModalOpen(false);
                // TODO handle error
                console.error("[EditorPage] Failed to save design content: ", error);
            });
        } else {
            console.debug("[EditorPage] Checking for conflicting Draft content");
            return drafts.getDraft(groupId as string, draftId as string, version as string).then(currentDraft => {
                if (currentDraft.contentId !== draft.contentId) {
                    console.debug(`[EditorPage] Detected Draft content conflict.  Expected '${draft.contentId}' but found '${currentDraft.contentId}'.'`);
                    // Uh oh, if we save now we'll be overwriting someone else's changes!
                    setPleaseWaitModalOpen(false);
                    setConfirmOverwriteModalOpen(true);
                    return Promise.resolve();
                } else {
                    console.debug("[EditorPage] Draft content not in conflict, saving...");
                    return onSave(true);
                }
            }).catch(error => {
                setPleaseWaitModalOpen(false);
                // TODO handle error
                console.error("[EditorPage] Failed to save design content: ", error);
            });
        }
    };

    const onFormat = (): void => {
        console.info("[EditorPage] Formatting content.");
        const formattedContent: string = formatContent(currentContent, draftContent.contentType);
        console.info("[EditorPage] New content is: ", formattedContent);
        setDraftContent({
            contentType: draftContent.contentType,
            content: formattedContent
        });
        setCurrentContent(formattedContent);
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
    const onEditorChange = (value: any): void => {
        setCurrentContent(value);
    };

    const onCompareContent = () => {
        setCompareModalOpen(true);
    };

    const closeCompareEditor = () => {
        setCompareModalOpen(false);
    };

    const notDraftEmptyState = (
        <EmptyState titleText={<Title headingLevel="h5" size="lg">Not a Draft</Title>} icon={WarningTriangleIcon} variant={EmptyStateVariant.sm}>
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
        <OpenApiEditor content={draftContent} onChange={onEditorChange}/>
    );

    const asyncapiEditor: React.ReactElement = (
        <AsyncApiEditor content={draftContent} onChange={onEditorChange}/>
    );

    const editor = (): React.ReactElement => {
        if (!draft.isDraft!) {
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
                <PageSection hasBodyWrapper={false}  id="section-editor" style={sectionEditorStyle}>
                    <div className="editor-parent" style={editorParentStyle} children={editor() as any} />
                </PageSection>
            </PageDataLoader>
            <CompareModal isOpen={isCompareModalOpen}
                onClose={closeCompareEditor}
                before={originalContent}
                beforeName={draft?.name || ""}
                after={currentContent}
                afterName={draft?.name || ""}/>
            <ConfirmOverwriteModal
                isOpen={isConfirmOverwriteModalOpen}
                onOverwrite={() => {
                    setConfirmOverwriteModalOpen(false);
                    onSave(true);
                }}
                onClose={() => setConfirmOverwriteModalOpen(false)} />
            <PleaseWaitModal message={pleaseWaitMessage}
                isOpen={isPleaseWaitModalOpen} />
        </PageErrorHandler>
    );

};
