import { useEffect, useRef, useState } from "react";
import { GroupsService } from "@services/useGroupsService.ts";
import { RenderPromptResponse, RenderPromptValidationError } from "@models/RenderPromptResponse.ts";
import {
    buildContentKey,
    buildInitialPanelState,
    buildVersionIdentity,
    parseObjectInput,
    renderErrorMessage,
    shouldAcceptRenderResponse,
    TestPanelInitialState
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
    /** Store the raw text of an object/array field and its parsed value in one update. */
    setObjectText: (name: string, text: string) => void;
    doRender: () => void;
};

/**
 * Owns Test Prompt panel state, including version/content resets and stale Render guards.
 * Pure helpers are tested in PromptTemplateTestPanel.utils.test.ts; the hook's reset and
 * race behavior is tested in usePromptTemplateTestPanelState.test.tsx.
 */
export const usePromptTemplateTestPanelState = (
    args: UsePromptTemplateTestPanelStateArgs
): PromptTemplateTestPanelState => {
    // Version identity at the time of the latest reset / Render. Used to ignore stale
    // async Render resolutions after the viewed version has moved on.
    const versionIdentity = buildVersionIdentity(args.groupId, args.artifactId, args.version);
    const versionIdentityRef = useRef(versionIdentity);
    const renderRequestIdRef = useRef(0);

    // Latest template/variables, read by the reset effect without depending on object identity.
    const templateRef = useRef(args.template);
    templateRef.current = args.template;
    const variablesRef = useRef(args.variables);
    variablesRef.current = args.variables;

    // Re-initialize when the version identity or the content (by value) changes. Comparing
    // content by value means an unmemoized parent cannot wipe in-progress input, while content
    // that arrives after mount, or after a version switch, still resets the form.
    const resetKey = `${versionIdentity}|${buildContentKey(args.template, args.variables)}`;
    const resetKeyRef = useRef(resetKey);

    // values and rawTexts live in one state object so they are built in a single pass and
    // can never drift apart.
    const [panelState, setPanelState] = useState<TestPanelInitialState>(
        () => buildInitialPanelState(args.template, args.variables)
    );
    const [renderedOutput, setRenderedOutput] = useState<string>("");
    const [validationErrors, setValidationErrors] = useState<RenderPromptValidationError[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string>("");

    useEffect(() => {
        if (resetKeyRef.current === resetKey) {
            return;
        }
        resetKeyRef.current = resetKey;
        versionIdentityRef.current = versionIdentity;
        renderRequestIdRef.current += 1;
        setPanelState(buildInitialPanelState(templateRef.current, variablesRef.current));
        setRenderedOutput("");
        setValidationErrors([]);
        setError("");
        setIsLoading(false);
    }, [resetKey]);

    const values = panelState.values;
    const rawTexts = panelState.rawTexts;

    const setValue = (name: string, value: any): void => {
        setPanelState(prev => ({ ...prev, values: { ...prev.values, [name]: value } }));
    };

    const setObjectText = (name: string, text: string): void => {
        const parsed = parseObjectInput(text).value;
        setPanelState(prev => ({
            values: { ...prev.values, [name]: parsed },
            rawTexts: { ...prev.rawTexts, [name]: text }
        }));
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
                setError(renderErrorMessage(err));
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
        setObjectText,
        doRender
    };
};
