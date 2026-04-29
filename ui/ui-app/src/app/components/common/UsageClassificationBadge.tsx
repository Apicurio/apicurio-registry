import React, { FunctionComponent } from "react";
import { Label } from "@patternfly/react-core";
import { If } from "@apicurio/common-ui-components";

export type UsageClassificationBadgeProps = {
    classification: string | undefined | null;
};

export const UsageClassificationBadge: FunctionComponent<UsageClassificationBadgeProps> = (props: UsageClassificationBadgeProps) => {

    return (
        <React.Fragment>
            <If condition={props.classification === "ACTIVE"}>
                <Label color="green">Active</Label>
            </If>
            <If condition={props.classification === "STALE"}>
                <Label color="orange">Stale</Label>
            </If>
            <If condition={props.classification === "DEAD"}>
                <Label color="red">Dead</Label>
            </If>
        </React.Fragment>
    );

};
