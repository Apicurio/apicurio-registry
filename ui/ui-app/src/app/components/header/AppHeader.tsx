import { FunctionComponent } from "react";
import { Brand, Masthead, MastheadBrand, MastheadContent, MastheadMain } from "@patternfly/react-core";
import { Link } from "react-router-dom";
import { AppHeaderToolbar } from "@app/components";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";


export type AppHeaderProps = object;


export const AppHeader: FunctionComponent<AppHeaderProps> = () => {
    const appNavigation: AppNavigation = useAppNavigation();
    const config: ConfigService = useConfigService();

    if (config.features().showMasthead !== undefined && !config.features().showMasthead) {
        return <></>;
    }

    const logoSrc: string = `${config.uiContextPath() || "/"}apicurio_registry_logo_reverse.svg`;

    return (
        <Masthead id="icon-router-link">
            <MastheadMain>
                <MastheadBrand component={props => <Link {...props} to={ appNavigation.createLink("/explore") } />}>
                    <Brand src={logoSrc} alt="Apicurio Registry" heights={{ default: "36px" }} />
                </MastheadBrand>
            </MastheadMain>
            <MastheadContent>
                <AppHeaderToolbar />
            </MastheadContent>
        </Masthead>
    );
};
