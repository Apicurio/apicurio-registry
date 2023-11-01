import { FunctionComponent } from "react";
import {
    Button,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody, EmptyStateFooter,
    EmptyStateHeader,
    EmptyStateIcon,
    EmptyStateVariant
} from "@patternfly/react-core";
import { PlusCircleIcon } from "@patternfly/react-icons";
import { If } from "@app/components";

/**
 * Properties
 */
export type RoleMappingsEmptyStateProps = {
    isFiltered?: boolean;
    onCreateRoleMapping?: () => void;
};


/**
 * Models the empty state for the Role Mappings page (when there are no roles).
 */
export const RoleMappingsEmptyState: FunctionComponent<RoleMappingsEmptyStateProps> = (props: RoleMappingsEmptyStateProps) => {
    return (
        <EmptyState variant={EmptyStateVariant.full}>
            <EmptyStateHeader titleText="No roles assigned" headingLevel="h4" icon={<EmptyStateIcon icon={PlusCircleIcon} />} />
            <If condition={() => props.isFiltered === true}>
                <EmptyStateBody>No role mappings match your filter settings.  Change your filter or perhaps create a new role mapping.</EmptyStateBody>
            </If>
            <If condition={() => !props.isFiltered}>
                <EmptyStateBody>
                    There are currently no role mappings configured for the registry.  Click the "Grant access" button above to grant access to a user.
                </EmptyStateBody>
                <EmptyStateFooter>
                    <EmptyStateActions>
                        <Button variant="primary" data-testid="btn-grant-access" onClick={props.onCreateRoleMapping}>Grant access</Button>
                    </EmptyStateActions>
                </EmptyStateFooter>
            </If>
        </EmptyState>
    );
};
