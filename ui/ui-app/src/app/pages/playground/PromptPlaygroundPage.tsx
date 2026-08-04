import { FunctionComponent } from "react";
import "./PromptPlaygroundPage.css";
import { useParams } from "react-router";
import {
    Alert,
    Card,
    CardBody,
    CardHeader,
    CardTitle,
    Label,
    Spinner,
    Title,
} from "@patternfly/react-core";
import { PromptTemplateEditor } from "@app/components/promptTemplate/PromptTemplateEditor";
import { usePromptPlayground } from "./hooks/usePromptPlayground";
import { PlaygroundVariableForm } from "./components/PlaygroundVariableForm";
import { PlaygroundRenderResult } from "./components/PlaygroundRenderResult";

/**
 * Prototype Prompt Template Playground page.
 *
 * Loads a PROMPT_TEMPLATE artifact from the registry, provides a Monaco editor
 * for viewing/editing the template string with live variable extraction, and a
 * test panel for filling in variable values and rendering via the real backend
 * endpoint. When the template has been locally modified, a client-side preview
 * is shown alongside the server-rendered result.
 *
 * This is a prototype page for LFX Mentorship proposal evaluation (issue #8425).
 * It does NOT modify the stored artifact — the editor is for local experimentation only.
 *
 * Known limitation: The /render endpoint reads template content from storage, not
 * from the request body. Therefore, server-rendered output always uses the stored
 * template version. Client-side preview fills this gap for local edits but does not
 * apply schema validation (type checking, enum constraints, range checks).
 */
export const PromptPlaygroundPage: FunctionComponent = () => {
    const { groupId, artifactId, version } = useParams<{
        groupId: string;
        artifactId: string;
        version: string;
    }>();

    const pg = usePromptPlayground(groupId, artifactId, version);

    // ---- Loading state ----

    if (pg.isLoadingContent) {
        return (
            <div className="prompt-playground-page">
                <div className="loading-container">
                    <Spinner size="xl" />
                </div>
            </div>
        );
    }

    // ---- Error state ----

    if (pg.loadError) {
        return (
            <div className="prompt-playground-page">
                <Alert variant="danger" title="Failed to load artifact">
                    {pg.loadError}
                </Alert>
            </div>
        );
    }

    // ---- Main layout ----

    return (
        <div className="prompt-playground-page">
            {/* Header */}
            <div className="playground-header">
                <Title headingLevel="h1" className="playground-title">
                    Prompt Template Playground
                </Title>
                <div className="playground-subtitle">
                    Prototype for LFX Mentorship proposal — issue #8425.
                    Edit the template, fill in variables, and render via the real backend.
                </div>
                <div className="playground-artifact-info">
                    <Label color="blue" isCompact>{groupId || "default"}</Label>{" "}
                    <Label color="purple" isCompact>{artifactId}</Label>{" "}
                    <Label color="grey" isCompact>v{version}</Label>
                </div>
            </div>

            {/* Split layout */}
            <div className="playground-layout">
                {/* Left: Editor */}
                <div className="playground-editor-panel">
                    <Card>
                        <CardHeader>
                            <CardTitle>
                                <Title headingLevel="h2" size="md">Template Editor</Title>
                            </CardTitle>
                        </CardHeader>
                        <CardBody>
                            {pg.isModified && (
                                <Alert
                                    variant="info"
                                    isInline
                                    isPlain
                                    title="Template modified locally"
                                    className="template-modified-alert"
                                >
                                    Server render uses the stored version. A client-side preview
                                    of your edits will also be shown.
                                </Alert>
                            )}
                            <PromptTemplateEditor
                                value={pg.currentTemplate}
                                onChange={pg.handleEditorChange}
                                onVariablesChange={pg.handleVariablesChange}
                            />
                        </CardBody>
                    </Card>
                </div>

                {/* Right: Test Panel */}
                <div className="playground-test-panel">
                    <Card>
                        <CardHeader>
                            <CardTitle>
                                <Title headingLevel="h2" size="md">Test Variables</Title>
                            </CardTitle>
                        </CardHeader>
                        <CardBody>
                            <PlaygroundVariableForm
                                variables={pg.variables}
                                values={pg.values}
                                isRendering={pg.isRendering}
                                onValueChange={pg.handleValueChange}
                                onRender={pg.handleRender}
                            />
                            <PlaygroundRenderResult
                                isRendering={pg.isRendering}
                                serverRendered={pg.serverRendered}
                                localPreview={pg.localPreview}
                                isModified={pg.isModified}
                                renderError={pg.renderError}
                                validationErrors={pg.validationErrors}
                            />
                        </CardBody>
                    </Card>
                </div>
            </div>
        </div>
    );
};
