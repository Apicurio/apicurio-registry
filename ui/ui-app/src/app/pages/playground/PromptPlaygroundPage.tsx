import { FunctionComponent, useCallback, useEffect, useState } from "react";
import "./PromptPlaygroundPage.css";
import { useParams } from "react-router";
import {
    ActionGroup,
    Alert,
    Button,
    Card,
    CardBody,
    CardHeader,
    CardTitle,
    Form,
    FormGroup,
    Label,
    Spinner,
    TextInput,
    Title,
} from "@patternfly/react-core";
import { PromptTemplateEditor } from "@app/components/promptTemplate/PromptTemplateEditor";
import { extractVariables } from "@app/components/promptTemplate/extractVariables";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { RenderPromptResponse } from "@models/RenderPromptResponse.ts";

/**
 * Performs simple client-side {{variable}} substitution.
 * This is a LOCAL preview only — no schema validation is applied.
 */
const localSubstitute = (template: string, variables: Record<string, string>): string => {
    return template.replace(/\{\{([^}]+)\}\}/g, (_match, rawName: string) => {
        const name = rawName.trim();
        if (name in variables && variables[name] !== "") {
            return variables[name];
        }
        return `{{${name}}}`;
    });
};

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

    const groups: GroupsService = useGroupsService();

    // Content state
    const [originalTemplate, setOriginalTemplate] = useState<string>("");
    const [currentTemplate, setCurrentTemplate] = useState<string>("");
    const [variables, setVariables] = useState<string[]>([]);
    const [values, setValues] = useState<Record<string, string>>({});

    // Loading / error state
    const [isLoadingContent, setIsLoadingContent] = useState<boolean>(true);
    const [loadError, setLoadError] = useState<string>("");

    // Render state
    const [isRendering, setIsRendering] = useState<boolean>(false);
    const [serverRendered, setServerRendered] = useState<string>("");
    const [localPreview, setLocalPreview] = useState<string>("");
    const [renderError, setRenderError] = useState<string>("");
    const [validationErrors, setValidationErrors] = useState<{ path?: string; message: string }[]>([]);

    const isModified = currentTemplate !== originalTemplate;

    // Load artifact content on mount
    useEffect(() => {
        if (!groupId || !artifactId || !version) {
            setLoadError("Missing route parameters: groupId, artifactId, and version are required.");
            setIsLoadingContent(false);
            return;
        }

        setIsLoadingContent(true);
        setLoadError("");

        groups.getArtifactVersionContent(groupId === "default" ? null : groupId, artifactId, version)
            .then((content: string) => {
                // Parse the content to extract the template field
                let parsed: { template?: string };
                try {
                    parsed = JSON.parse(content);
                } catch {
                    // Try YAML-like parsing — the content might be YAML
                    // For this prototype, we handle JSON; YAML would need the yaml package
                    setLoadError("Failed to parse artifact content as JSON.");
                    return;
                }

                const templateText = parsed.template || "";
                setOriginalTemplate(templateText);
                setCurrentTemplate(templateText);

                const vars = extractVariables(templateText);
                setVariables(vars);

                // Initialize empty values for each variable
                const initialValues: Record<string, string> = {};
                vars.forEach((v) => {
                    initialValues[v] = "";
                });
                setValues(initialValues);
            })
            .catch((err: unknown) => {
                const message = err instanceof Error ? err.message : "Failed to load artifact content";
                setLoadError(message);
            })
            .finally(() => {
                setIsLoadingContent(false);
            });
    }, [groupId, artifactId, version]);

    const handleEditorChange = useCallback((newValue: string) => {
        setCurrentTemplate(newValue);
    }, []);

    const handleVariablesChange = useCallback((newVars: string[]) => {
        setVariables(newVars);
        setValues((prev) => {
            const next: Record<string, string> = {};
            newVars.forEach((v) => {
                next[v] = prev[v] ?? "";
            });
            return next;
        });
    }, []);

    const handleValueChange = useCallback((varName: string, newValue: string) => {
        setValues((prev) => ({ ...prev, [varName]: newValue }));
    }, []);

    const handleRender = useCallback(() => {
        if (!groupId || !artifactId || !version) {
            return;
        }

        setIsRendering(true);
        setRenderError("");
        setValidationErrors([]);
        setServerRendered("");
        setLocalPreview("");

        // Always call the real backend (uses stored template)
        const gid = groupId === "default" ? null : groupId;
        groups.renderPromptTemplate(gid, artifactId, version, values)
            .then((response: RenderPromptResponse) => {
                setServerRendered(response.rendered || "");
                if (response.validationErrors && response.validationErrors.length > 0) {
                    setValidationErrors(response.validationErrors);
                }
            })
            .catch((err: unknown) => {
                const message = (err && typeof err === "object" && "message" in err)
                    ? (err as { message: string }).message
                    : "Error rendering prompt template";
                setRenderError(message);
            })
            .finally(() => {
                setIsRendering(false);
            });

        // If template was modified, also compute client-side preview
        if (isModified) {
            const preview = localSubstitute(currentTemplate, values);
            setLocalPreview(preview);
        }
    }, [groupId, artifactId, version, values, isModified, currentTemplate, groups]);

    // ---- Render ----

    if (isLoadingContent) {
        return (
            <div className="prompt-playground-page">
                <div className="loading-container">
                    <Spinner size="xl" />
                </div>
            </div>
        );
    }

    if (loadError) {
        return (
            <div className="prompt-playground-page">
                <Alert variant="danger" title="Failed to load artifact">
                    {loadError}
                </Alert>
            </div>
        );
    }

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
                            {isModified && (
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
                                value={currentTemplate}
                                onChange={handleEditorChange}
                                onVariablesChange={handleVariablesChange}
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
                            <Form className="test-panel-form">
                                {variables.length === 0 && (
                                    <span style={{ color: "var(--pf-t--global--text--color--subtle)", fontStyle: "italic" }}>
                                        No variables detected in template.
                                    </span>
                                )}
                                {variables.map((varName) => (
                                    <FormGroup
                                        key={varName}
                                        label={varName}
                                        fieldId={`playground-var-${varName}`}
                                    >
                                        <TextInput
                                            type="text"
                                            id={`playground-var-${varName}`}
                                            value={values[varName] || ""}
                                            onChange={(_event, val) => handleValueChange(varName, val)}
                                            aria-label={varName}
                                            placeholder={`Enter value for {{${varName}}}`}
                                        />
                                    </FormGroup>
                                ))}
                                <ActionGroup>
                                    <Button
                                        variant="primary"
                                        onClick={handleRender}
                                        isDisabled={isRendering || variables.length === 0}
                                        isLoading={isRendering}
                                    >
                                        Render
                                    </Button>
                                </ActionGroup>
                            </Form>

                            {isRendering && <Spinner size="md" />}

                            {renderError && (
                                <Alert variant="danger" title="Render Error" className="validation-errors">
                                    {renderError}
                                </Alert>
                            )}

                            {validationErrors.length > 0 && (
                                <Alert variant="warning" title="Validation Errors" className="validation-errors">
                                    <ul>
                                        {validationErrors.map((ve, i) => (
                                            <li key={i}>{ve.path ? `${ve.path}: ` : ""}{ve.message}</li>
                                        ))}
                                    </ul>
                                </Alert>
                            )}

                            {/* Server-rendered output */}
                            {serverRendered && (
                                <div className="rendered-section">
                                    <Title headingLevel="h3" size="md">Server-Rendered (validated)</Title>
                                    <div className="render-source-label">
                                        <Label color="green" isCompact>Server</Label>{" "}
                                        Uses stored template with backend validation
                                    </div>
                                    <div className="rendered-output">{serverRendered}</div>
                                </div>
                            )}

                            {/* Client-side preview (only when template is modified) */}
                            {localPreview && isModified && (
                                <div className="rendered-section">
                                    <Title headingLevel="h3" size="md">Local Preview (unvalidated)</Title>
                                    <div className="render-source-label">
                                        <Label color="orange" isCompact>Local</Label>{" "}
                                        Client-side substitution — no schema validation applied
                                    </div>
                                    <div className="rendered-output">{localPreview}</div>
                                </div>
                            )}
                        </CardBody>
                    </Card>
                </div>
            </div>
        </div>
    );
};
