import { FunctionComponent } from "react";
import { Navigate } from "react-router";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { PageProperties } from "@app/pages";


/**
 * The root redirect page.
 */
export const RootRedirectPage: FunctionComponent<PageProperties> = () => {
    const appNav: AppNavigation = useAppNavigation();

    const redirect: string = appNav.createLink("/dashboard");
    return (
        <Navigate to={redirect} replace />
    );

};
