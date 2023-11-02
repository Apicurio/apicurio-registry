import {
    EmptyState,
    EmptyStateHeader,
    EmptyStateIcon,
    Spinner,
} from "@patternfly/react-core";

export function Loading() {
    return (
        <EmptyState>
            <EmptyStateHeader
                titleText="Loading"
                headingLevel="h4"
                icon={<EmptyStateIcon icon={Spinner} />}
            />
        </EmptyState>
    );
}
