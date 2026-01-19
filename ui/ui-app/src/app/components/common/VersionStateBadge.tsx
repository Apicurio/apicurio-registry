import React, { FunctionComponent } from "react";
import { Label } from "@patternfly/react-core";
import { If } from "@apicurio/common-ui-components";
import { SearchedVersion, VersionMetaData } from "@sdk/lib/generated-client/models";

export type VersionStateBadgeProps = {
    version: SearchedVersion | VersionMetaData | undefined;
    showEnabled?: boolean;
};


export const VersionStateBadge: FunctionComponent<VersionStateBadgeProps> = (props: VersionStateBadgeProps) => {

    return (
        <React.Fragment>
            <If condition={props.version?.state === "DRAFT"}>
                <Label color="grey">Draft</Label>
            </If>
            <If condition={props.version?.state === "DEPRECATED"}>
                <Label color="orange">Deprecated</Label>
            </If>
            <If condition={props.version?.state === "DISABLED"}>
                <Label color="red">Disabled</Label>
            </If>
            <If condition={props.showEnabled === true && props.version?.state === "ENABLED"}>
                <Label color="green">Enabled</Label>
            </If>
        </React.Fragment>
    );

};
