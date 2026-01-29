import { FunctionComponent } from "react";
import "./GroupPageHeader.css";
import { Button, Flex, FlexItem, Content, ContentVariants } from "@patternfly/react-core";
import { IfAuth, IfFeature } from "@app/components";
import { If } from "@apicurio/common-ui-components";


/**
 * Properties
 */
export type GroupPageHeaderProps = {
    title: string;
    groupId: string;
    onDeleteGroup: () => void;
};

/**
 * Models the page header for the Group page.
 */
export const GroupPageHeader: FunctionComponent<GroupPageHeaderProps> = (props: GroupPageHeaderProps) => {
    return (
        <Flex className="example-border">
            <FlexItem>
                <Content>
                    <Content component={ContentVariants.h1}>{ props.title }</Content>
                </Content>
            </FlexItem>
            <FlexItem align={{ default: "alignRight" }}>
                <If condition={props.groupId !== "default"}>
                    <IfAuth isDeveloper={true}>
                        <IfFeature feature="readOnly" isNot={true}>
                            <IfFeature feature="deleteGroup" is={true}>
                                <Button id="delete-artifact-button" variant="danger"
                                    data-testid="header-btn-delete" onClick={props.onDeleteGroup}>Delete group</Button>
                            </IfFeature>
                        </IfFeature>
                    </IfAuth>
                </If>
            </FlexItem>
        </Flex>
    );
};
