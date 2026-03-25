import React, { FunctionComponent, useEffect, useState } from "react";
import {
    AccessErrorPage,
    ConnectionFailedErrorPage,
    ErrorPage,
    NotFoundErrorPage,
    RateLimitErrorPage
} from "@app/components";
import { PageError } from "@app/pages/PageError.ts";
import { useReauthenticationService } from "@services/useReauthenticationService.ts";
import { isErrorStatus } from "@utils/rest.utils.ts";

/**
 * Properties
 */
export type PageErrorHandlerProps = {
    error: PageError | undefined;
    children?: React.ReactNode;
};

/**
 * Helper to load the data for a Page in the UI.
 * @param props
 * @constructor
 */
export const PageErrorHandler: FunctionComponent<PageErrorHandlerProps> = (props: PageErrorHandlerProps) => {
    const reauthentication = useReauthenticationService();
    const [isReauthenticationPending, setReauthenticationPending] = useState<boolean>(
        reauthentication.isReauthenticationPending()
    );

    useEffect(() => {
        return reauthentication.subscribe(setReauthenticationPending);
    }, [reauthentication]);

    const isError = (): boolean => {
        return props.error !== undefined;
    };
    const isFetchError = (): boolean => {
        return props.error !== undefined && props.error.error instanceof TypeError && (props.error.error as TypeError).message.includes("fetch");
    };
    const is404Error = (): boolean => {
        return props.error !== undefined && isErrorStatus(props.error.error, 404);
    };
    const is403Error = (): boolean => {
        return props.error !== undefined && isErrorStatus(props.error.error, 403);
    };
    const is401Error = (): boolean => {
        return props.error !== undefined && isErrorStatus(props.error.error, 401);
    };
    const is419Error = (): boolean => {
        return props.error !== undefined && isErrorStatus(props.error.error, 419);
    };

    if (isError()) {
        // Re-authentication already took over the UX, so do not replace the page with a generic 401 error.
        if (is401Error() && isReauthenticationPending) {
            return props.children;
        }
        if (is403Error()) {
            return (
                <AccessErrorPage error={props.error}/>
            );
        } else if (is419Error()) {
            return (
                <RateLimitErrorPage error={props.error}/>
            );
        } else if (is404Error()) {
            return (
                <NotFoundErrorPage error={props.error}/>
            );
        } else if (isFetchError()) {
            return (
                <ConnectionFailedErrorPage error={props.error}/>
            );
        } else {
            return (
                <ErrorPage error={props.error}/>
            );
        }
    } else {
        return props.children;
    }
};
