import { useCallback, useEffect, useRef, useState } from "react";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { RenderPromptResponse } from "@models/RenderPromptResponse.ts";
import { extractVariables } from "@app/components/promptTemplate/extractVariables";

export type RenderValidationError = { path?: string; message: string };

export type UsePromptPlaygroundResult = {
    // Content
    originalTemplate: string;
    currentTemplate: string;
    variables: string[];
    values: Record<string, string>;
    isModified: boolean;
    // Loading
    isLoadingContent: boolean;
    loadError: string;
    // Render
    isRendering: boolean;
    serverRendered: string;
    localPreview: string;
    renderError: string;
    validationErrors: RenderValidationError[];
    // Handlers
    handleEditorChange: (newValue: string) => void;
    handleVariablesChange: (newVars: string[]) => void;
    handleValueChange: (varName: string, newValue: string) => void;
    handleRender: () => void;
};

/**
 * Manages all state and side-effects for the Prompt Template Playground page.
 *
 * Separated from the view layer so the page component stays a thin layout shell.
 * Owns: artifact loading, editor state, variable form state, and render orchestration.
 */
export const usePromptPlayground = (
    groupId: string | undefined,
    artifactId: string | undefined,
    version: string | undefined,
): UsePromptPlaygroundResult => {
    const groups: GroupsService = useGroupsService();

    // Stable ref to groups service — avoids breaking useCallback memoization
    // since useGroupsService() returns a new object literal on each render.
    const groupsRef = useRef<GroupsService>(groups);
    useEffect(() => {
        groupsRef.current = groups;
    }); // no dep array — always stays current

    // --- Content state ---
    const [originalTemplate, setOriginalTemplate] = useState<string>("");
    const [currentTemplate, setCurrentTemplate] = useState<string>("");
    const [variables, setVariables] = useState<string[]>([]);
    const [values, setValues] = useState<Record<string, string>>({});

    // --- Loading / error state ---
    const [isLoadingContent, setIsLoadingContent] = useState<boolean>(true);
    const [loadError, setLoadError] = useState<string>("");

    // --- Render state ---
    const [isRendering, setIsRendering] = useState<boolean>(false);
    const [serverRendered, setServerRendered] = useState<string>("");
    const [localPreview, setLocalPreview] = useState<string>("");
    const [renderError, setRenderError] = useState<string>("");
    const [validationErrors, setValidationErrors] = useState<RenderValidationError[]>([]);

    const isModified = currentTemplate !== originalTemplate;

    // --- Load artifact content on mount ---
    useEffect(() => {
        if (!groupId || !artifactId || !version) {
            setLoadError("Missing route parameters: groupId, artifactId, and version are required.");
            setIsLoadingContent(false);
            return;
        }

        setIsLoadingContent(true);
        setLoadError("");

        const gid = groupId === "default" ? null : groupId;
        groupsRef.current.getArtifactVersionContent(gid, artifactId, version)
            .then((content: string) => {
                let parsed: { template?: string };
                try {
                    parsed = JSON.parse(content);
                } catch {
                    setLoadError("Failed to parse artifact content as JSON.");
                    return;
                }

                const templateText = parsed.template || "";
                setOriginalTemplate(templateText);
                setCurrentTemplate(templateText);

                const vars = extractVariables(templateText);
                setVariables(vars);

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

    // --- Handlers ---

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
        groupsRef.current.renderPromptTemplate(gid, artifactId, version, values)
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
            const preview = currentTemplate.replace(/\{\{([^}]+)\}\}/g, (_match, rawName: string) => {
                const name = rawName.trim();
                return (name in values && values[name] !== "") ? values[name] : `{{${name}}}`;
            });
            setLocalPreview(preview);
        }
    }, [groupId, artifactId, version, values, isModified, currentTemplate]);

    return {
        originalTemplate, currentTemplate, variables, values, isModified,
        isLoadingContent, loadError,
        isRendering, serverRendered, localPreview, renderError, validationErrors,
        handleEditorChange, handleVariablesChange, handleValueChange, handleRender,
    };
};
