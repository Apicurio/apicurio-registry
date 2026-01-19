import React, { FunctionComponent } from "react";
import { Link } from "react-router-dom";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { If } from "@apicurio/common-ui-components";

let testIdCounter: number = 1;

export type SearchArtifactNameProps = {
    groupId: string|null;
    artifactId: string;
    name: string;
}


export const SearchArtifactName: FunctionComponent<SearchArtifactNameProps> = (props: SearchArtifactNameProps) => {
    const appNav: AppNavigation = useAppNavigation();

    const artifactLink = (): string => {
        const gid: string = encodeURIComponent(props.groupId || "default");
        const aid: string = encodeURIComponent(props.artifactId);
        const link: string = `/explore/${gid}/${aid}`;
        return appNav.createLink(link);
    };

    const counter = testIdCounter++;
    const testId = (prefix: string): string => {
        return `${prefix}-${counter}`;
    };

    return (
        <React.Fragment>
            <Link className="id" data-testid={testId("artifacts-lnk-view-id-")} to={artifactLink()}>{props.artifactId}</Link>
            <If condition={props.name !== null && props.name !== undefined && props.name !== ""}>
                <Link className="name" data-testid={testId("artifacts-lnk-view-")} to={artifactLink()}>{props.name}</Link>
            </If>
        </React.Fragment>
    );
};
