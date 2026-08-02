import React, { Component, ErrorInfo, ReactNode } from "react";
import {
    Button,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    PageSection,
} from "@patternfly/react-core";
import { ExclamationTriangleIcon } from "@patternfly/react-icons";

export type ErrorBoundaryProps = {
    children: ReactNode;
    location?: string;
};

export type ErrorBoundaryState = {
    hasError: boolean;
    error: Error | undefined;
};

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
    constructor(props: ErrorBoundaryProps) {
        super(props);
        this.state = { hasError: false, error: undefined };
    }

    static getDerivedStateFromError(error: Error): ErrorBoundaryState {
        return { hasError: true, error };
    }

    componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
        console.error("[ErrorBoundary] Uncaught error:", error, errorInfo);
    }

    componentDidUpdate(prevProps: ErrorBoundaryProps): void {
        if (this.state.hasError && this.props.location && prevProps.location !== this.props.location) {
            this.setState({ hasError: false, error: undefined });
        }
    }

    reloadPage = (): void => {
        window.location.reload();
    };

    render(): ReactNode {
        if (this.state.hasError) {
            return (
                <PageSection hasBodyWrapper={false} className="ps_error">
                    <div className="centerizer">
                        <EmptyState
                            headingLevel="h4"
                            icon={ExclamationTriangleIcon}
                            titleText="Something went wrong"
                        >
                            <EmptyStateBody>
                                An unexpected error occurred. Try reloading the page.
                                If the issue persists, reach out to your administrator.
                            </EmptyStateBody>
                            <EmptyStateFooter>
                                <EmptyStateActions>
                                    <Button
                                        variant="primary"
                                        onClick={this.reloadPage}
                                        data-testid="error-boundary-reload-btn"
                                    >
                                        Reload page
                                    </Button>
                                </EmptyStateActions>
                            </EmptyStateFooter>
                        </EmptyState>
                    </div>
                </PageSection>
            );
        }

        return this.props.children;
    }
}
