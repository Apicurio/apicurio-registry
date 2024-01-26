import React, { FunctionComponent, useEffect, useState } from "react";
import { AuthService, useAuth } from "@apicurio/common-ui-components";
import { useUserService } from "@services/useUserService.ts";

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
    const [isAuthenticated, setIsAuthenticated] = useState(false);

    const user = useUserService();
    const auth: AuthService = useAuth();

    useEffect(() => {
        auth.isAuthenticated().then(setIsAuthenticated);
    }, []);

    const accept = () => {
        let rval: boolean = true;
        if (props.enabled !== undefined) {
            rval = rval && (auth.isAuthEnabled() === props.enabled);
        }
        if (props.isAuthenticated !== undefined) {
            rval = rval && (isAuthenticated === props.isAuthenticated);
        }
        if (props.isAdmin !== undefined) {
            rval = rval && (user.isUserAdmin() === props.isAdmin);
        }
        if (props.isDeveloper !== undefined) {
            rval = rval && (user.isUserDeveloper(props.owner) === props.isDeveloper);
        }
        if (props.isOwner !== undefined && props.owner) {
            if (props.isOwner) {
                rval = rval && (user.isUserId(props.owner));
            } else {
                rval = rval && (!user.isUserId(props.owner));
            }
        }
        if (props.isAdminOrOwner === true && props.owner) {
            rval = rval && (user.isUserAdmin() || user.isUserId(props.owner));
        }
        return rval;
    };

    if (accept()) {
        return <React.Fragment children={props.children} />;
    } else {
        return <React.Fragment />;
    }

};
