import React, { FunctionComponent, ReactNode } from "react";
import { useLocation } from "react-router";
import { ErrorBoundary } from "./ErrorBoundary";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";

export type ErrorBoundaryWithRouterProps = {
    children: ReactNode;
};

export const ErrorBoundaryWithRouter: FunctionComponent<ErrorBoundaryWithRouterProps> = (props) => {
    const location = useLocation();
    const logger: LoggerService = useLoggerService();
    const appNav: AppNavigation = useAppNavigation();

    return (
        <ErrorBoundary
            location={location.pathname}
            logger={logger}
            onNavigateHome={() => appNav.navigateTo("/dashboard")}
        >
            {props.children}
        </ErrorBoundary>
    );
};
