import React, { FunctionComponent, ReactNode } from "react";
import { useLocation } from "react-router";
import { ErrorBoundary } from "./ErrorBoundary";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { shouldOfferNavigateHome } from "./ErrorBoundary.utils";

export type ErrorBoundaryWithRouterProps = {
    children: ReactNode;
};

const HOME_PATH: string = "/dashboard";

export const ErrorBoundaryWithRouter: FunctionComponent<ErrorBoundaryWithRouterProps> = (props) => {
    const location = useLocation();
    const logger: LoggerService = useLoggerService();
    const appNav: AppNavigation = useAppNavigation();

    const offerNavigateHome: boolean = shouldOfferNavigateHome(location.pathname, appNav.createLink(HOME_PATH));

    return (
        <ErrorBoundary
            location={location.pathname}
            logger={logger}
            onNavigateHome={offerNavigateHome ? () => appNav.navigateTo(HOME_PATH) : undefined}
        >
            {props.children}
        </ErrorBoundary>
    );
};
