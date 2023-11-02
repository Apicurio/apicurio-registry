import React, { FunctionComponent } from "react";
import "./ArtifactList.css";
import { Link } from "react-router-dom";
import { useAppNavigation } from "@hooks/useAppNavigation.ts";

let testIdCounter: number = 1;

export type ArtifactNameProps = {
    groupId: string|null;
    id: string;
    name: string;
}


export const ArtifactName: FunctionComponent<ArtifactNameProps> = (props: ArtifactNameProps) => {
    const appNav = useAppNavigation();

    const artifactLink = (): string => {
        const groupId: string = props.groupId == null ? "default" : props.groupId;
        const link: string = `/artifacts/${ encodeURIComponent(groupId)}/${ encodeURIComponent(props.id) }`;
        return appNav.createLink(link);
    };

    const counter = testIdCounter++;
    const testId = (prefix: string): string => {
        return `${prefix}-${counter}`;
    };

    return props.name ? (
        <React.Fragment>
            <Link className="name" data-testid={testId("artifacts-lnk-view-")} to={artifactLink()}>{props.name}</Link>
            <Link className="id" data-testid={testId("artifacts-lnk-view-id-")} to={artifactLink()}>{props.id}</Link>
        </React.Fragment>
    ) : (
        <React.Fragment>
            <Link className="name" data-testid={testId("artifacts-lnk-view-")} to={artifactLink()}>{props.id}</Link>
        </React.Fragment>
    );

};
