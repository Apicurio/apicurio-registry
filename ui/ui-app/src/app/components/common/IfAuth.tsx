import React, { FunctionComponent } from "react";
import { Services } from "@services/services";
import { AuthService } from "@services/auth";

/**
 * Properties
 */
export type IfAuthProps = {
    enabled?: boolean;
    isAuthenticated?: boolean;
    isAdmin?: boolean;
    isDeveloper?: boolean;
    isOwner?: boolean;
    isAdminOrOwner?: boolean;
    owner?: string;
    children?: React.ReactNode;
};

/**
 * Wrapper around a set of arbitrary child elements and displays them only if the
 * indicated authentication parameters are true.
 */
export const IfAuth: FunctionComponent<IfAuthProps> = (props: IfAuthProps) => {

    const accept = () => {
        const auth: AuthService = Services.getAuthService();
        let rval: boolean = true;
        if (props.enabled !== undefined) {
            rval = rval && (auth.isAuthenticationEnabled() === props.enabled);
        }
        if (props.isAuthenticated !== undefined) {
            rval = rval && (auth.isAuthenticated() === props.isAuthenticated);
        }
        if (props.isAdmin !== undefined) {
            rval = rval && (auth.isUserAdmin() === props.isAdmin);
        }
        if (props.isDeveloper !== undefined) {
            rval = rval && (auth.isUserDeveloper(props.owner) === props.isDeveloper);
        }
        if (props.isOwner !== undefined && props.owner) {
            if (props.isOwner) {
                rval = rval && (auth.isUserId(props.owner));
            } else {
                rval = rval && (!auth.isUserId(props.owner));
            }
        }
        if (props.isAdminOrOwner === true && props.owner) {
            rval = rval && (auth.isUserAdmin() || auth.isUserId(props.owner));
        }
        return rval;
    };

    if (accept()) {
        return <React.Fragment children={props.children} />;
    } else {
        return <React.Fragment />;
    }

};
