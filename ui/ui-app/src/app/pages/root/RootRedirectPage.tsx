import { FunctionComponent } from "react";
import { Navigate } from "react-router-dom";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";


/**
 * The root redirect page.
 */
export const RootRedirectPage: FunctionComponent<any> = () => {
    const appNav: AppNavigation = useAppNavigation();

    const redirect: string = appNav.createLink("/explore");
    return (
        <Navigate to={redirect} replace />
    );

};
