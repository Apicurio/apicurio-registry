import { FunctionComponent } from "react";
import { Navigate } from "react-router-dom";
import { useAppNavigation } from "@hooks/useAppNavigation.ts";


export type RootRedirectPageProps = {
    // No props
}

/**
 * The root redirect page.
 */
export const RootRedirectPage: FunctionComponent<RootRedirectPageProps> = () => {
    const appNav = useAppNavigation();

    const redirect: string = appNav.createLink("/artifacts");
    return (
        <Navigate to={redirect} replace />
    );

};
