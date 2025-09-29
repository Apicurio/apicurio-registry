import React, { FunctionComponent } from "react";
import { Link } from "react-router-dom";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import {Tooltip} from "@patternfly/react-core";
import {DesktopIcon} from "@patternfly/react-icons";
import {If} from "@apicurio/common-ui-components";

let testIdCounter: number = 1;

/**
 * Properties
 */
export type ArtifactGroupProps = {
    groupId: string|null;
};


/**
 * Models an artifact group in a list of artifacts or groups.
 */
export const ArtifactGroup: FunctionComponent<ArtifactGroupProps> = (props: ArtifactGroupProps) => {
    const appNav: AppNavigation = useAppNavigation();

    const groupLink = (): string => {
        const groupId: string = props.groupId == null ? "default" : props.groupId;
        const link: string = `/explore/${ encodeURIComponent(groupId)}`;
        return appNav.createLink(link);
    };

    const counter = testIdCounter++;
    const testId = (prefix: string): string => {
        return `${prefix}-${counter}`;
    };

    const style = (): string => {
        return !props.groupId ? "nogroup" : "group";
    };

    return (
        <React.Fragment>
            <Link className={style()} data-testid={testId("group-lnk-view")} to={groupLink()}>
                <span>{ props.groupId }</span>
                <If condition={props.groupId === "default"}>
                    <Tooltip content="System defined">
                        <DesktopIcon style={{ marginLeft: "8px" }} />
                    </Tooltip>
                </If>
            </Link>
        </React.Fragment>
    );

};
