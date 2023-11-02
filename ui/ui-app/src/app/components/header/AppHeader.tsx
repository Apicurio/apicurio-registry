import React, { FunctionComponent } from "react";
import "./AppHeader.css";
import { AvatarDropdown, IfAuth } from "@app/components";
import {
    PageHeader,
    PageHeaderTools,
    PageHeaderToolsGroup,
    PageHeaderToolsItem
} from "@patternfly/react-core/deprecated";
import { AppNavigation, useAppNavigation } from "@hooks/useAppNavigation.ts";


export type AppHeaderProps = {
    // No properties.
};


export const AppHeader: FunctionComponent<AppHeaderProps> = () => {
    const appNavigation: AppNavigation = useAppNavigation();

    const logoProps = {
        href: appNavigation.createLink("/")
    };

    const logo: React.ReactNode = (
        <div className="app-logo">
            <img className="pf-c-brand logo-make" src="/apicurio_registry_logo_reverse.svg" alt="Apicurio Registry"/>
        </div>
    );

    const headerActions: React.ReactElement = (
        <PageHeaderTools className="header-toolbar">
            <PageHeaderToolsGroup>
                <PageHeaderToolsItem id="avatar">
                    <IfAuth enabled={true}>
                        <AvatarDropdown />
                    </IfAuth>
                </PageHeaderToolsItem>
            </PageHeaderToolsGroup>
        </PageHeaderTools>
    );

    return (
        <PageHeader logo={logo} logoProps={logoProps} showNavToggle={false} headerTools={headerActions} />
    );

};
