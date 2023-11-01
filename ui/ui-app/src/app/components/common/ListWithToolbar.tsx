import React, { FunctionComponent } from "react";
import { If } from "./If.tsx";
import { IsLoading } from "./IsLoading.tsx";
import { Alert } from "@patternfly/react-core";

/**
 * Properties
 */
export type ListWithToolbarProps = {
    toolbar: React.ReactNode;
    alwaysShowToolbar?: boolean;
    emptyState: React.ReactNode;
    filteredEmptyState: React.ReactNode;
    isLoading: boolean;
    loadingComponent?: React.ReactNode;
    isError: boolean;
    errorComponent?: React.ReactNode;
    isFiltered: boolean;
    isEmpty: boolean;
    children?: React.ReactNode;
};

/**
 * Wrapper around a set of arbitrary child elements and displays them only if the
 * indicated condition is true.
 */
export const ListWithToolbar: FunctionComponent<ListWithToolbarProps> = (
    { toolbar, alwaysShowToolbar, emptyState, filteredEmptyState, isLoading, isError, loadingComponent, errorComponent, isEmpty, isFiltered, children }: ListWithToolbarProps) => {

    const showToolbar: boolean = alwaysShowToolbar || !isEmpty || isFiltered || isError;
    if (!errorComponent) {
        errorComponent = (
            <div style={{ padding: "15px", backgroundColor: "white" }}>
                <Alert isInline variant="danger" title="Error: Something went wrong!">
                    <p>
                        Something went wrong with the action you attempted, but we're not sure what it was.
                        Try reloading the page and hopef for a better result, or contact your admin to report
                        the error.
                    </p>
                </Alert>
            </div>
        );
    }

    return (
        <React.Fragment>
            <If condition={showToolbar} children={toolbar} />
            <IsLoading condition={isLoading} loadingComponent={loadingComponent}>
                <If condition={!isEmpty && !isError} children={children} />
                <If condition={isEmpty && isFiltered && !isError} children={filteredEmptyState} />
                <If condition={isEmpty && !isFiltered && !isError} children={emptyState} />
                <If condition={isError} children={errorComponent} />
            </IsLoading>
        </React.Fragment>
    );
};
