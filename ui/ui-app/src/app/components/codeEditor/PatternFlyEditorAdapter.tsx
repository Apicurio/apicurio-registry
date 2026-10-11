import { FunctionComponent } from "react";
import { CodeEditor } from "@patternfly/react-code-editor";
import type { CodeEditorProps, Language } from "@patternfly/react-code-editor";

/**
 * Properties for "RegistryPatternFlyCodeEditor".  Identical to PatternFly's own "CodeEditorProps",
 * except "language" also accepts the literal string values of PatternFly's "Language" enum, so
 * callers do not need a runtime import of that enum just to pass a language.
 */
export type RegistryPatternFlyCodeEditorProps = Omit<CodeEditorProps, "language"> & {
    language?: `${Language}`;
};

/**
 * Lazily-loaded adapter around PatternFly's "CodeEditor".  Only ever imported through
 * "RegistryEditors.tsx"'s lazy component declaration - never imported directly by application
 * code - so that PatternFly's Monaco wrapper is not pulled into the initial application bundle.
 */
export const PatternFlyEditorAdapter: FunctionComponent<RegistryPatternFlyCodeEditorProps> = (
    props: RegistryPatternFlyCodeEditorProps
) => {
    return <CodeEditor {...props as CodeEditorProps} />;
};

export default PatternFlyEditorAdapter;
