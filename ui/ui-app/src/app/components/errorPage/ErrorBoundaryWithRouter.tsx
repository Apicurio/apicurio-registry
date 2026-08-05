import React, { FunctionComponent } from "react";
import { useLocation } from "react-router";
import { ErrorBoundary, ErrorBoundaryProps } from "./ErrorBoundary";

export const ErrorBoundaryWithRouter: FunctionComponent<ErrorBoundaryProps> = (props) => {
    const location = useLocation();
    return <ErrorBoundary location={location.pathname}>{props.children}</ErrorBoundary>;
};
