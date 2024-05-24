import { FunctionComponent } from "react";
import "./GroupInfoTabContent.css";
import "@app/styles/empty.css";
import { IfAuth, IfFeature } from "@app/components";
import {
    Button,
    Card,
    CardBody,
    CardTitle,
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm,
    Divider,
    Flex,
    FlexItem,
    Icon,
    Label,
    Truncate
} from "@patternfly/react-core";
import { IndustryIcon, OutlinedFolderIcon, PencilAltIcon } from "@patternfly/react-icons";
import { FromNow, If } from "@apicurio/common-ui-components";
import { GroupMetaData } from "@models/groupMetaData.model.ts";

/**
 * Properties
 */
export type GroupInfoTabContentProps = {
    group: GroupMetaData;
    onEditMetaData: () => void;
    onChangeOwner: () => void;
};

/**
 * Models the content of the Artifact Info tab.
 */
export const GroupInfoTabContent: FunctionComponent<GroupInfoTabContentProps> = (props: GroupInfoTabContentProps) => {

    const description = (): string => {
        return props.group.description || "No description";
    };

    return (
        <div className="group-tab-content">
            <div className="group-basics">
                <Card>
                    <CardTitle>
                        <div className="title-and-type">
                            <Flex>
                                <FlexItem className="type"><Icon><OutlinedFolderIcon /></Icon></FlexItem>
                                <FlexItem className="title">Group metadata</FlexItem>
                                <FlexItem className="actions" align={{ default: "alignRight" }}>
                                    <IfAuth isDeveloper={true}>
                                        <IfFeature feature="readOnly" isNot={true}>
                                            <Button id="edit-action"
                                                data-testid="group-btn-edit"
                                                onClick={props.onEditMetaData}
                                                variant="link"><PencilAltIcon />{" "}Edit</Button>
                                        </IfFeature>
                                    </IfAuth>
                                </FlexItem>
                            </Flex>
                        </div>
                    </CardTitle>
                    <Divider />
                    <CardBody>
                        <DescriptionList className="metaData" isCompact={true}>
                            <DescriptionListGroup>
                                <DescriptionListTerm>ID</DescriptionListTerm>
                                <DescriptionListDescription data-testid="group-details-id">{props.group.groupId}</DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Description</DescriptionListTerm>
                                <DescriptionListDescription
                                    data-testid="group-details-description"
                                    className={!props.group.description ? "empty-state-text" : ""}
                                >
                                    { description() }
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Created</DescriptionListTerm>
                                <DescriptionListDescription data-testid="group-details-created-on">
                                    <FromNow date={props.group.createdOn} />
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <If condition={props.group.owner !== undefined && props.group.owner !== ""}>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Owner</DescriptionListTerm>
                                    <DescriptionListDescription data-testid="group-details-created-by">
                                        <span>{props.group.owner}</span>
                                        <span>
                                            <IfAuth isAdminOrOwner={true} owner={props.group.owner}>
                                                <IfFeature feature="readOnly" isNot={true}>
                                                    <Button id="edit-action"
                                                        data-testid="group-btn-change-owner"
                                                        onClick={props.onChangeOwner}
                                                        variant="link"><PencilAltIcon /></Button>
                                                </IfFeature>
                                            </IfAuth>
                                        </span>
                                    </DescriptionListDescription>
                                </DescriptionListGroup>
                            </If>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Modified</DescriptionListTerm>
                                <DescriptionListDescription data-testid="group-details-modified-on">
                                    <FromNow date={props.group.modifiedOn} />
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Labels</DescriptionListTerm>
                                {!props.group.labels || !Object.keys(props.group.labels).length ?
                                    <DescriptionListDescription data-testid="group-details-labels" className="empty-state-text">No labels</DescriptionListDescription> :
                                    <DescriptionListDescription data-testid="group-details-labels">{Object.entries(props.group.labels).map(([key, value]) =>
                                        <Label key={`label-${key}`} color="purple" style={{ marginBottom: "2px", marginRight: "5px" }}>
                                            <Truncate className="label-truncate" content={`${key}=${value}`} />
                                        </Label>
                                    )}</DescriptionListDescription>
                                }
                            </DescriptionListGroup>
                        </DescriptionList>
                        <div className="actions">
                        </div>
                    </CardBody>
                </Card>
            </div>
            <div className="group-rules">
                <Card>
                    <CardTitle>
                        <div className="rules-label">Group-specific rules</div>
                    </CardTitle>
                    <Divider />
                    <CardBody>
                        <p style={{ paddingBottom: "15px" }}>
                            Manage the content rules for this group. Each group-specific rule can be
                            individually enabled, configured, and disabled. Group-specific rules override
                            the equivalent global rules.
                        </p>
                        <p>
                            <b><IndustryIcon /> Under construction </b>
                        </p>
                        {/*<RuleList*/}
                        {/*    isGlobalRules={false}*/}
                        {/*    rules={props.rules}*/}
                        {/*    onEnableRule={props.onEnableRule}*/}
                        {/*    onDisableRule={props.onDisableRule}*/}
                        {/*    onConfigureRule={props.onConfigureRule}*/}
                        {/*/>*/}
                    </CardBody>
                </Card>
            </div>
        </div>
    );

};
