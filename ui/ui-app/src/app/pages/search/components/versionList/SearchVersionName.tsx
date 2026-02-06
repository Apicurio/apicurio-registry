import React, { FunctionComponent } from "react";
import { Link } from "react-router";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { If } from "@apicurio/common-ui-components";

let testIdCounter: number = 1;

export type SearchVersionNameProps = {
    groupId: string|null;
    artifactId: string;
    version: string;
    name: string;
}


export const SearchVersionName: FunctionComponent<SearchVersionNameProps> = (props: SearchVersionNameProps) => {
    const appNav: AppNavigation = useAppNavigation();

    const artifactLink = (): string => {
        const gid: string = encodeURIComponent(props.groupId || "default");
        const aid: string = encodeURIComponent(props.artifactId);
        const link: string = `/explore/${gid}/${aid}`;
        return appNav.createLink(link);
    };

    const versionLink = (): string => {
        const gid: string = encodeURIComponent(props.groupId || "default");
        const aid: string = encodeURIComponent(props.artifactId);
        const ver: string = encodeURIComponent(props.version);
        const link: string = `/explore/${gid}/${aid}/versions/${ver}`;
        return appNav.createLink(link);
    };

    const counter = testIdCounter++;
    const testId = (prefix: string): string => {
        return `${prefix}-${counter}`;
    };

    return (
        <React.Fragment>
            <Link className="id" data-testid={testId("artifacts-lnk-view-id-")} to={artifactLink()}>{props.artifactId}</Link>
            <Link className="version" data-testid={testId("versions-lnk-view-id-")} to={versionLink()}>{props.version}</Link>
            <If condition={props.name !== null && props.name !== undefined && props.name !== ""}>
                <Link className="name" data-testid={testId("artifacts-lnk-view-")} to={versionLink()}>{props.name}</Link>
            </If>
        </React.Fragment>
    );

};
