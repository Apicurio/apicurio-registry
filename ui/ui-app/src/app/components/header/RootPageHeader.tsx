import React, { FunctionComponent } from "react";
import { Tab, Tabs, TabTitleText } from "@patternfly/react-core";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { useConfigService } from "@services/useConfigService.ts";
import { useUserService } from "@services/useUserService.ts";
import {
    DASHBOARD_PAGE_IDX,
    DRAFTS_PAGE_IDX,
    EXPLORE_PAGE_IDX,
    ROLES_PAGE_IDX,
    RULES_PAGE_IDX,
    SEARCH_PAGE_IDX,
    SETTINGS_PAGE_IDX
} from "@app/pages";


/**
 * Properties
 */
export type RootPageHeaderProps = {
    tabKey: number;
};

export const RootPageHeader: FunctionComponent<RootPageHeaderProps> = (props: RootPageHeaderProps) => {
    const appNavigation: AppNavigation = useAppNavigation();
    const config = useConfigService();
    const user = useUserService();

    const handleTabClick = (_event: React.MouseEvent<HTMLElement, MouseEvent>, eventKey: number | string): void => {
        if (eventKey !== props.tabKey) {
            if (eventKey === DASHBOARD_PAGE_IDX) {
                // navigate to dashboard
                appNavigation.navigateTo("/dashboard");
            }
            if (eventKey === EXPLORE_PAGE_IDX) {
                // navigate to artifacts
                appNavigation.navigateTo("/explore");
            }
            if (eventKey === SEARCH_PAGE_IDX) {
                // navigate to artifacts
                appNavigation.navigateTo("/search");
            }
            if (eventKey === DRAFTS_PAGE_IDX) {
                // navigate to artifacts
                appNavigation.navigateTo("/drafts");
            }
            if (eventKey === DRAFTS_PAGE_IDX) {
                // navigate to artifacts
                appNavigation.navigateTo("/drafts");
            }
            if (eventKey === RULES_PAGE_IDX) {
                // navigate to global rules
                appNavigation.navigateTo("/rules");
            }
            if (eventKey === ROLES_PAGE_IDX) {
                // navigate to permissions page
                appNavigation.navigateTo("/roles");
            }
            if (eventKey === SETTINGS_PAGE_IDX) {
                // navigate to settings page
                appNavigation.navigateTo("/settings");
            }
        }
    };

    // Always available: Dashboard, Explore and Search tabs
    const tabs: any[] = [
        <Tab data-testid="dashboard-tab" key={DASHBOARD_PAGE_IDX} eventKey={DASHBOARD_PAGE_IDX} title={<TabTitleText>Dashboard</TabTitleText>} />,
        <Tab data-testid="explore-tab" key={EXPLORE_PAGE_IDX} eventKey={EXPLORE_PAGE_IDX} title={<TabTitleText>Explore</TabTitleText>} />,
        <Tab data-testid="search-tab" key={SEARCH_PAGE_IDX} eventKey={SEARCH_PAGE_IDX} title={<TabTitleText>Search</TabTitleText>} />,
    ];

    // Add Drafts tab if mutability is enabled and user is a developer or admin
    if (config.featureDraftMutability() && user.isUserDeveloper()) {
        tabs.push(
            <Tab data-testid="drafts-tab" key={DRAFTS_PAGE_IDX} eventKey={DRAFTS_PAGE_IDX} title={<TabTitleText>Drafts</TabTitleText>} />,
        );
    }

    // Always show the Global Rules tab
    tabs.push(
        <Tab data-testid="rules-tab" key={RULES_PAGE_IDX} eventKey={RULES_PAGE_IDX} title={<TabTitleText>Global rules</TabTitleText>} />
    );

    // Show Roles tab if RBAC is enabled and the user is an admin
    if (config.featureRoleManagement() && user.isUserAdmin()) {
        tabs.push(
            <Tab data-testid="access-tab" key={ROLES_PAGE_IDX} eventKey={ROLES_PAGE_IDX} title={<TabTitleText>Access</TabTitleText>} />
        );
    }

    // Show Settings tab if feature is enabled and the user is an admin
    if (config.featureSettings() && user.isUserAdmin()) {
        tabs.push(
            <Tab data-testid="settings-tab" key={SETTINGS_PAGE_IDX} eventKey={SETTINGS_PAGE_IDX} title={<TabTitleText>Settings</TabTitleText>} />
        );
    }
    return (
        <div>
            <Tabs className="root-tabs" activeKey={props.tabKey} onSelect={handleTabClick} children={tabs} />
        </div>
    );

};
