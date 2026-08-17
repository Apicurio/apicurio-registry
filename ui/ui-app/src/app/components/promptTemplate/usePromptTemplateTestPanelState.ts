import { useEffect, useRef, useState } from "react";
import { GroupsService } from "@services/useGroupsService.ts";
import { RenderPromptResponse, RenderPromptValidationError } from "@models/RenderPromptResponse.ts";
import {
    buildInitialPanelState,
    buildVersionIdentity,
    shouldAcceptRenderResponse,
    shouldResetPanelState
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
    // Version identity at the time of the latest reset / Render. Used to ignore stale
    // async Render resolutions after the viewed version has moved on.
    const versionIdentity = buildVersionIdentity(args.groupId, args.artifactId, args.version);
    const versionIdentityRef = useRef(versionIdentity);
    const renderRequestIdRef = useRef(0);

    // Whether artifact content had arrived as of the latest reset. Lets the reset effect
    // distinguish "content just loaded" from "same content, new object reference".
    const contentWasPresentRef = useRef(args.template !== undefined || args.variables !== undefined);

    const [values, setValues] = useState<Record<string, any>>(
        () => buildInitialPanelState(args.template, args.variables).values
    );
    const [rawTexts, setRawTexts] = useState<Record<string, string>>(
        () => buildInitialPanelState(args.template, args.variables).rawTexts
    );
    const [renderedOutput, setRenderedOutput] = useState<string>("");
    const [validationErrors, setValidationErrors] = useState<RenderPromptValidationError[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string>("");

    // Reset form values and render results when the viewed version identity changes, or
    // when the artifact content transitions from absent to present (async load after
    // mount). Guarded by shouldResetPanelState rather than raw reference equality, so a
    // caller that fails to memoize template/variables cannot wipe in-progress user input
    // on an incidental re-render.
    useEffect(() => {
        const contentPresent = args.template !== undefined || args.variables !== undefined;
        const versionChanged = versionIdentityRef.current !== versionIdentity;
        if (!shouldResetPanelState(versionChanged, contentPresent, contentWasPresentRef.current)) {
            return;
        }
        contentWasPresentRef.current = contentPresent;
        versionIdentityRef.current = versionIdentity;
        renderRequestIdRef.current += 1;
        const initial = buildInitialPanelState(args.template, args.variables);
        setValues(initial.values);
        setRawTexts(initial.rawTexts);
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
