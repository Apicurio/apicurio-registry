import { useEffect, useRef, useState } from "react";
import { GroupsService } from "@services/useGroupsService.ts";
import { RenderPromptResponse, RenderPromptValidationError } from "@models/RenderPromptResponse.ts";
import {
    buildInitialRawTexts,
    buildInitialValues,
    buildVersionIdentity,
    shouldAcceptRenderResponse
} from "./PromptTemplateTestPanel.utils";
import { VariableSchema } from "./promptTemplateVariables";

export type PromptTemplateTestPanelIdentity = {
    groupId: string;
    artifactId: string;
    version: string;
};

export type UsePromptTemplateTestPanelStateArgs = PromptTemplateTestPanelIdentity & {
    template?: string;
    variables: Record<string, VariableSchema> | VariableSchema[] | undefined;
    groups: Pick<GroupsService, "renderPromptTemplate">;
};

export type PromptTemplateTestPanelState = {
    values: Record<string, any>;
    rawTexts: Record<string, string>;
    renderedOutput: string;
    validationErrors: RenderPromptValidationError[];
    isLoading: boolean;
    error: string;
    setValue: (name: string, value: any) => void;
    setRawText: (name: string, text: string) => void;
    doRender: () => void;
};

/**
 * Owns Test Prompt panel state, including version-identity resets and stale Render guards.
 * Reset/race contracts are unit-tested via helpers in PromptTemplateTestPanel.utils.
 */
export const usePromptTemplateTestPanelState = (
    args: UsePromptTemplateTestPanelStateArgs
): PromptTemplateTestPanelState => {
    // Keep the latest template/variables for the version-identity reset without depending on
    // object identity — the parent may rebuild these on every render for a given version.
    const templateRef = useRef(args.template);
    templateRef.current = args.template;
    const variablesRef = useRef(args.variables);
    variablesRef.current = args.variables;

    // Version identity at the time of the latest reset / Render. Used to ignore stale
    // async Render resolutions after the viewed version has moved on.
    const versionIdentity = buildVersionIdentity(args.groupId, args.artifactId, args.version);
    const versionIdentityRef = useRef(versionIdentity);
    const renderRequestIdRef = useRef(0);

    const [values, setValues] = useState<Record<string, any>>(
        () => buildInitialValues(args.template, args.variables)
    );
    const [rawTexts, setRawTexts] = useState<Record<string, string>>(
        () => buildInitialRawTexts(args.template, args.variables)
    );
    const [renderedOutput, setRenderedOutput] = useState<string>("");
    const [validationErrors, setValidationErrors] = useState<RenderPromptValidationError[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string>("");

    // Reset form values and render results whenever the viewed version identity changes,
    // OR when the artifact content (template/variables) transitions from absent to present.
    // The parent memoizes template/variables, so these refs only change when the version
    // key changes or when the artifact content is loaded asynchronously — user input isn't
    // wiped on incidental re-renders.
    useEffect(() => {
        versionIdentityRef.current = versionIdentity;
        renderRequestIdRef.current += 1;
        setValues(buildInitialValues(templateRef.current, variablesRef.current));
        setRawTexts(buildInitialRawTexts(templateRef.current, variablesRef.current));
        setRenderedOutput("");
        setValidationErrors([]);
        setError("");
        setIsLoading(false);
    }, [versionIdentity, args.template, args.variables]);

    const setValue = (name: string, value: any): void => {
        setValues(prev => ({ ...prev, [name]: value }));
    };

    const setRawText = (name: string, text: string): void => {
        setRawTexts(prev => ({ ...prev, [name]: text }));
    };

    const doRender = (): void => {
        const requestId = ++renderRequestIdRef.current;
        const requestVersionIdentity = versionIdentityRef.current;
        setIsLoading(true);
        setError("");
        setValidationErrors([]);
        setRenderedOutput("");

        let gid: string | null = args.groupId;
        if (gid === "default") {
            gid = null;
        }

        args.groups.renderPromptTemplate(gid, args.artifactId, args.version, values)
            .then((response: RenderPromptResponse) => {
                if (!shouldAcceptRenderResponse(
                    requestId,
                    renderRequestIdRef.current,
                    requestVersionIdentity,
                    versionIdentityRef.current
                )) {
                    return;
                }
                setRenderedOutput(response.rendered || "");
                if (response.validationErrors && response.validationErrors.length > 0) {
                    setValidationErrors(response.validationErrors);
                }
            })
            .catch((err: any) => {
                if (!shouldAcceptRenderResponse(
                    requestId,
                    renderRequestIdRef.current,
                    requestVersionIdentity,
                    versionIdentityRef.current
                )) {
                    return;
                }
                setError(err?.message || "Error rendering prompt template");
            })
            .finally(() => {
                if (!shouldAcceptRenderResponse(
                    requestId,
                    renderRequestIdRef.current,
                    requestVersionIdentity,
                    versionIdentityRef.current
                )) {
                    return;
                }
                setIsLoading(false);
            });
    };

    return {
        values,
        rawTexts,
        renderedOutput,
        validationErrors,
        isLoading,
        error,
        setValue,
        setRawText,
        doRender
    };
};
