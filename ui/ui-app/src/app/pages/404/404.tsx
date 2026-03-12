import { FunctionComponent } from "react";
import {
    Button,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    EmptyStateVariant,
    PageSection,
    
} from "@patternfly/react-core";
import { ExclamationCircleIcon } from "@patternfly/react-icons";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { PageProperties } from "@app/pages";


/**
 * The "not found" page.
 */
export const NotFoundPage: FunctionComponent<PageProperties> = () => {
    const appNavigation: AppNavigation = useAppNavigation();

    return  (
        <PageSection hasBodyWrapper={false} className="ps_rules-header" >
            <EmptyState  headingLevel="h4" icon={ExclamationCircleIcon}  titleText="404 Error: page not found" variant={EmptyStateVariant.full}>
                <EmptyStateBody>
                    This page couldn't be found.  If you think this is a bug, please report the issue.
                </EmptyStateBody>
                <EmptyStateFooter>
                    <EmptyStateActions>
                        <Button variant="primary"
                            data-testid="error-btn-artifacts"
                            onClick={() => appNavigation.navigateTo("/explore")}>Show all artifacts</Button>
                    </EmptyStateActions>
                </EmptyStateFooter>
            </EmptyState>
        </PageSection>
    );

};
