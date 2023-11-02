import React, { FunctionComponent } from "react";
import { AccessErrorPage, ErrorPage, RateLimitErrorPage } from "@app/components";
import { PageError } from "@app/pages/PageError.ts";

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

    const isError = (): boolean => {
        return props.error !== undefined;
    };
    const is403Error = (): boolean => {
        return props.error && props.error.error.status && (props.error.error.status == 403);
    };
    const is419Error = (): boolean => {
        return props.error && props.error.error.status && (props.error.error.status == 419);
    };
    
    if (isError()) {
        if (is403Error()) {
            return (
                <AccessErrorPage error={props.error}/>
            );
        } else if (is419Error()) {
            return (
                <RateLimitErrorPage error={props.error}/>
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
