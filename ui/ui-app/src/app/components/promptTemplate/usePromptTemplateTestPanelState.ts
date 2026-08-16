import { useEffect, useMemo, useRef, useState } from "react";
import { GroupsService } from "@services/useGroupsService.ts";
import { RenderPromptResponse, RenderPromptValidationError } from "@models/RenderPromptResponse.ts";
import {
    buildInitialValues,
    buildVersionIdentity,
    hasAllRequiredValues,
    isAbortError,
    reconcileForPanel,
    shouldAcceptRenderResponse
} from "./PromptTemplateTestPanel.utils";
import { ReconciledVariable, VariableSchema } from "./promptTemplateVariables";

const AUTO_RENDER_DEBOUNCE_MS = 500;

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
    reconciledVariables: ReconciledVariable[];
    renderedOutput: string;
    validationErrors: RenderPromptValidationError[];
    isLoading: boolean;
    error: string;
    setValue: (name: string, value: any) => void;
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
    const [renderedOutput, setRenderedOutput] = useState<string>("");
    const [validationErrors, setValidationErrors] = useState<RenderPromptValidationError[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string>("");

    const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const abortControllerRef = useRef<AbortController | null>(null);
    const isDirtyRef = useRef(false);

    const clearDebounceTimer = (): void => {
        if (debounceTimerRef.current !== null) {
            clearTimeout(debounceTimerRef.current);
            debounceTimerRef.current = null;
        }
    };

    // Reset form values and render results whenever the viewed version identity changes.
    // Depend only on the version key — not template/variables by reference — so an unstable
    // parent object cannot wipe in-progress user input on every re-render.
    useEffect(() => {
        clearDebounceTimer();
        abortControllerRef.current?.abort();
        isDirtyRef.current = false;
        versionIdentityRef.current = versionIdentity;
        renderRequestIdRef.current += 1;
        setValues(buildInitialValues(templateRef.current, variablesRef.current));
        setRenderedOutput("");
        setValidationErrors([]);
        setError("");
        setIsLoading(false);
    }, [versionIdentity]);

    const setValue = (name: string, value: any): void => {
        isDirtyRef.current = true;
        setValues(prev => ({ ...prev, [name]: value }));
    };

    const doRender = (): void => {
        clearDebounceTimer();

        abortControllerRef.current?.abort();
        const controller = new AbortController();
        abortControllerRef.current = controller;

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

        args.groups.renderPromptTemplate(gid, args.artifactId, args.version, values, controller.signal)
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
                if (isAbortError(err)) {
                    return;
                }
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
                if (controller.signal.aborted) {
                    return;
                }
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

    // Reconciled variables for the template + declared schema for the current version.
    // Keyed on versionIdentity (not template/variables object identity) so an unstable parent
    // object cannot tear down and re-arm the debounce timer on every render.
    const reconciledVariables = useMemo(
        () => reconcileForPanel(templateRef.current, variablesRef.current),
        [versionIdentity]
    );

    // Auto-render 500ms after the last user edit, as long as every required variable is filled
    // and the panel has been dirtied via setValue — skips firing on mount/version load and
    // while the user is still mid-edit on a required field.
    useEffect(() => {
        clearDebounceTimer();
        debounceTimerRef.current = setTimeout(() => {
            debounceTimerRef.current = null;
            if (!isDirtyRef.current || !hasAllRequiredValues(reconciledVariables, values)) {
                return;
            }
            doRender();
        }, AUTO_RENDER_DEBOUNCE_MS);

        return () => {
            clearDebounceTimer();
        };
    }, [values, reconciledVariables]);

    // Cancel any in-flight request on unmount so a resolved response never applies after the
    // panel is gone.
    useEffect(() => {
        return () => {
            abortControllerRef.current?.abort();
        };
    }, []);

    return {
        values,
        reconciledVariables,
        renderedOutput,
        validationErrors,
        isLoading,
        error,
        setValue,
        doRender
    };
};
