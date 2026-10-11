import { Component, ErrorInfo, FunctionComponent, ReactNode, Suspense, useEffect, useState } from "react";
import "./EditorLoadBoundary.css";
import { Alert, Button } from "@patternfly/react-core";

const SLOW_LOAD_THRESHOLD_MS: number = 30_000;

/**
 * Resolves the width/height props accepted by our editor wrappers ("100%", a number of pixels,
 * or PatternFly's "sizeToFit") into a concrete CSS dimension usable for the pending/error
 * fallback UI.  "sizeToFit" has no meaning outside of a mounted Monaco editor, so it falls back
 * to a fixed minimum height instead of being passed through as invalid CSS.
 */
function toFallbackDimension(value: string | number | undefined, fallback: string): string {
    if (value === undefined || value === "sizeToFit") {
        return fallback;
    }
    return typeof value === "number" ? `${value}px` : value;
}

export type EditorLoadBoundaryProps = {
    children: ReactNode;
    height?: string | number;
    width?: string | number;
    /**
     * Optional plain text (for example, error diagnostic content) to display, escaped, if the
     * code editor cannot be loaded.  Without this, only the generic failure message is shown.
     */
    diagnosticFallbackText?: string;
};

/**
 * The pending state shown by "Suspense" while the shared Monaco runtime (and, for the
 * PatternFly-backed editor, the adapter module) is loading.  Displays a slow-loading notice if
 * loading takes longer than 30 seconds, without interrupting the underlying import.
 */
const LoadingFallback: FunctionComponent<{ height: string; width: string }> = ({ height, width }) => {
    const [isSlow, setIsSlow] = useState<boolean>(false);

    useEffect(() => {
        const timer: ReturnType<typeof setTimeout> = setTimeout(() => setIsSlow(true), SLOW_LOAD_THRESHOLD_MS);
        return () => clearTimeout(timer);
    }, []);

    return (
        <div className="editor-load-boundary__status" style={{ height, width }} role="status">
            <span>{isSlow ?
                "The code editor is taking longer than expected. You can keep waiting or reload the page." :
                "Loading code editor…"}
            </span>
        </div>
    );
};

export type EditorLoadErrorProps = {
    height: string;
    width: string;
    diagnosticFallbackText?: string;
};

/**
 * The local failure state shown when the code editor could not be loaded (for example, the
 * Monaco runtime chunk failed to download).  Deliberately does not retry automatically or fall
 * back to any external asset source.
 */
const EditorLoadError: FunctionComponent<EditorLoadErrorProps> = ({ height, width, diagnosticFallbackText }) => {
    const reload = (): void => {
        window.location.reload();
    };

    return (
        <div className="editor-load-boundary__error" style={{ height, width }}>
            <Alert
                variant="danger"
                title="Unable to load the code editor"
                data-testid="editor-load-boundary-error"
            >
                <p>Try reloading the page. If the issue persists, reach out to your administrator.</p>
                <Button variant="link" isInline={true} onClick={reload} data-testid="editor-load-boundary-reload-btn">
                    Reload page
                </Button>
            </Alert>
            {diagnosticFallbackText !== undefined && (
                <pre className="editor-load-boundary__diagnostic" data-testid="editor-load-boundary-diagnostic">
                    {diagnosticFallbackText}
                </pre>
            )}
        </div>
    );
};

type InternalErrorBoundaryProps = {
    children: ReactNode;
    height: string;
    width: string;
    diagnosticFallbackText?: string;
};

type InternalErrorBoundaryState = {
    hasError: boolean;
};

/**
 * A local (non-full-page) error boundary.  Isolates failures in loading/mounting the code editor
 * from the rest of the page, so a broken editor does not take down surrounding navigation.
 */
class InternalErrorBoundary extends Component<InternalErrorBoundaryProps, InternalErrorBoundaryState> {
    constructor(props: InternalErrorBoundaryProps) {
        super(props);
        this.state = { hasError: false };
    }

    static getDerivedStateFromError(): InternalErrorBoundaryState {
        return { hasError: true };
    }

    componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
        console.error("[EditorLoadBoundary] Failed to load the code editor:", error, errorInfo);
    }

    render(): ReactNode {
        if (this.state.hasError) {
            return (
                <EditorLoadError
                    height={this.props.height}
                    width={this.props.width}
                    diagnosticFallbackText={this.props.diagnosticFallbackText}
                />
            );
        }
        return this.props.children;
    }
}

/**
 * Shared local loading/error boundary for every lazily-loaded Registry code editor
 * ("RegistryCodeEditor", "RegistryDiffEditor", "RegistryPatternFlyCodeEditor").  Renders a
 * pending state (with slow-loading guidance) while the shared Monaco runtime is loading, and a
 * local failure state - never a fallback to any external/CDN asset source - if loading fails.
 */
export const EditorLoadBoundary: FunctionComponent<EditorLoadBoundaryProps> = (props: EditorLoadBoundaryProps) => {
    const height: string = toFallbackDimension(props.height, "100%");
    const width: string = toFallbackDimension(props.width, "100%");

    return (
        <InternalErrorBoundary height={height} width={width} diagnosticFallbackText={props.diagnosticFallbackText}>
            <Suspense fallback={<LoadingFallback height={height} width={width} />}>
                {props.children}
            </Suspense>
        </InternalErrorBoundary>
    );
};

export { LoadingFallback };
