import React, { FunctionComponent } from "react";
import { Tab, Tabs, TabTitleText } from "@patternfly/react-core";
import { IfAuth } from "@app/components";
import { Services } from "@services/services.ts";
import { AppNavigation, useAppNavigation } from "@hooks/useAppNavigation.ts";


/**
 * Properties
 */
export type RootPageHeaderProps = {
    tabKey: number;
};


export const RootPageHeader: FunctionComponent<RootPageHeaderProps> = (props: RootPageHeaderProps) => {
    const appNavigation: AppNavigation = useAppNavigation();

    const handleTabClick = (_event: React.MouseEvent<HTMLElement, MouseEvent>, eventKey: number | string): void => {
        if (eventKey !== props.tabKey) {
            if (eventKey === 0) {
                // navigate to artifacts
                appNavigation.navigateTo("/artifacts");
            }
            if (eventKey === 1) {
                // navigate to global rules
                appNavigation.navigateTo("/rules");
            }
            if (eventKey === 2) {
                // navigate to permissions page
                appNavigation.navigateTo("/roles");
            }
            if (eventKey === 3) {
                // navigate to settings page
                appNavigation.navigateTo("/settings");
            }
        }
    };

    const tabs: any[] = [
        <Tab data-testid="artifacts-tab" key={0} eventKey={0} title={<TabTitleText>Artifacts</TabTitleText>} />,
        <Tab data-testid="rules-tab" key={1} eventKey={1} title={<TabTitleText>Global rules</TabTitleText>} />
    ];
    if (Services.getConfigService().featureRoleManagement()) {
        tabs.push(
            <Tab data-testid="access-tab" key={2} eventKey={2} title={<TabTitleText>Access</TabTitleText>} />
        );
    }
    if (Services.getConfigService().featureSettings() && Services.getAuthService().isUserAdmin()) {
        tabs.push(
            <Tab data-testid="settings-tab" key={3} eventKey={3} title={<TabTitleText>Settings</TabTitleText>} />
        );
    }
    return (
        <div>
            <Tabs className="root-tabs" activeKey={props.tabKey} onSelect={handleTabClick} children={tabs} />
        </div>
    );

};
