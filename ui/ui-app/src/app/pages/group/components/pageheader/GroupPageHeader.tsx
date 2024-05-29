import { FunctionComponent } from "react";
import "./GroupPageHeader.css";
import { Button, Flex, FlexItem, Text, TextContent, TextVariants } from "@patternfly/react-core";
import { IfAuth, IfFeature } from "@app/components";


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
                <TextContent>
                    <Text component={TextVariants.h1}>{ props.title }</Text>
                </TextContent>
            </FlexItem>
            <FlexItem align={{ default: "alignRight" }}>
                <IfAuth isDeveloper={true}>
                    <IfFeature feature="readOnly" isNot={true}>
                        <Button id="delete-artifact-button" variant="danger"
                            data-testid="header-btn-delete" onClick={props.onDeleteGroup}>Delete group</Button>
                    </IfFeature>
                </IfAuth>
            </FlexItem>
        </Flex>
    );
};
