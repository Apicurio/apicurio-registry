import React, { FunctionComponent } from "react";
import { EmptyState, EmptyStateBody, EmptyStateVariant, Title } from "@patternfly/react-core";

/**
 * Properties
 */
export interface IfNotEmptyProps {
    collection?: any[] | undefined;
    emptyState?: React.ReactNode;
    emptyStateTitle?: string;
    emptyStateMessage?: string;
    children?: React.ReactNode;
}

/**
 * Wrapper around a set of arbitrary child elements and displays them only if the
 * provided collection is not empty.  If the provided collection is empty, then
 * an empty state control is displayed instead.
 */
export const IfNotEmpty: FunctionComponent<IfNotEmptyProps> = ({collection, emptyState, emptyStateTitle, emptyStateMessage, children}: IfNotEmptyProps) => {
    const isEmpty = () => {
        return !collection || collection.length === 0;
    };

    const empty: React.ReactNode = emptyState || (
        <EmptyState variant={EmptyStateVariant.xs}>
            <Title headingLevel="h4" size="md">{emptyStateTitle || "None found"}</Title>
            <EmptyStateBody>{emptyStateMessage || "No items found."}</EmptyStateBody>
        </EmptyState>
    );

    return isEmpty() ?
        <React.Fragment children={empty}/> :
        <React.Fragment children={children} />
};
