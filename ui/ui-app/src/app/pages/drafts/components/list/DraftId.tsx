import React, { FunctionComponent } from "react";
import { Link } from "react-router";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { If } from "@apicurio/common-ui-components";

export type DraftIdProps = {
    groupId: string|null;
    draftId: string;
    version: string;
    name: string;
    testId: string;
}


export const DraftId: FunctionComponent<DraftIdProps> = (props: DraftIdProps) => {
    const appNav: AppNavigation = useAppNavigation();

    const draftLink = (): string => {
        const groupId: string = props.groupId == null ? "default" : props.groupId;
        const gid: string = encodeURIComponent(groupId);
        const did: string = encodeURIComponent(props.draftId);
        const ver: string = encodeURIComponent(props.version);

        const link: string = `/explore/${gid}/${did}/versions/${ver}`;
        return appNav.createLink(link);
    };

    return (
        <React.Fragment>
            <Link title={`Explore version ${props.version} of artifact ${props.draftId}`} className="id" data-testid={`${props.testId}-id`} to={draftLink()}>
                <span className="draft-id">{props.draftId}</span>
                <span className="version">{props.version}</span>
            </Link>
            <If condition={props.name !== null && props.name !== undefined && props.name !== ""}>
                <span className="name">{props.name}</span>
            </If>
        </React.Fragment>
    );
};
