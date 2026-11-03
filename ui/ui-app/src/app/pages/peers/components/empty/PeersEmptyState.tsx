import { FunctionComponent } from "react";
import {
    Button,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    EmptyStateVariant
} from "@patternfly/react-core";
import { ExclamationCircleIcon, PlusCircleIcon } from "@patternfly/react-icons";

/**
 * Properties
 */
export type PeersEmptyStateProps = {
    // The server answered that federation is switched off on this registry.
    isFederationDisabled?: boolean;
    // When true there is no "Add remote registry" button (read-only UI).
    isReadOnly?: boolean;
    onAddPeer?: () => void;
};


/**
 * Models the empty state for the Remote registries page: either federation is switched off,
 * or it is on and no remote registry has been added yet.
 */
export const PeersEmptyState: FunctionComponent<PeersEmptyStateProps> = (props: PeersEmptyStateProps) => {
    if (props.isFederationDisabled) {
        return (
            <EmptyState headingLevel="h4" icon={ExclamationCircleIcon} titleText="Federation is not enabled"
                variant={EmptyStateVariant.full} data-testid="federation-disabled">
                <EmptyStateBody>
                    Remote registries are only available when federation is enabled on this registry.
                    Ask an administrator to set <code>apicurio.federation.enabled</code> to <code>true</code>.
                </EmptyStateBody>
            </EmptyState>
        );
    }
    return (
        <EmptyState headingLevel="h4" icon={PlusCircleIcon} titleText="No remote registries"
            variant={EmptyStateVariant.full} data-testid="no-peers">
            <EmptyStateBody>
                No remote registries are configured. Add one to include its public agents in federated agent search.
            </EmptyStateBody>
            { props.isReadOnly ? null :
                <EmptyStateFooter>
                    <EmptyStateActions>
                        <Button variant="primary" data-testid="btn-add-peer-empty" onClick={props.onAddPeer}>
                            Add remote registry
                        </Button>
                    </EmptyStateActions>
                </EmptyStateFooter>
            }
        </EmptyState>
    );
};
