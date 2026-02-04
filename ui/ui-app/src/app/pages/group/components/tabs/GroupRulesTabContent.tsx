import { FunctionComponent } from "react";
import "./GroupRulesTabContent.css";
import "@app/styles/empty.css";
import { RuleList, RuleListType } from "@app/components";
import {
    Card,
    CardBody,
    CardTitle,
    Divider,
    EmptyState,
    EmptyStateBody,
    EmptyStateVariant
} from "@patternfly/react-core";
import { GroupMetaData, Rule } from "@sdk/lib/generated-client/models";
import { WarningTriangleIcon } from "@patternfly/react-icons";
import { If } from "@apicurio/common-ui-components";

/**
 * Properties
 */
export type GroupRulesTabContentProps = {
    group: GroupMetaData;
    rules: Rule[];
    onEnableRule: (ruleType: string) => void;
    onDisableRule: (ruleType: string) => void;
    onConfigureRule: (ruleType: string, config: string) => void;
};

/**
 * Models the content of the Group Rules tab.
 */
export const GroupRulesTabContent: FunctionComponent<GroupRulesTabContentProps> = (props: GroupRulesTabContentProps) => {

    return (
        <div className="group-rules-tab-content">
            <If condition={props.group.groupId === "default"}>
                <div className="group-rules-empty" style={{ width: "100%", marginTop: "20px" }}>
                    <EmptyState titleText="Rules not available" icon={WarningTriangleIcon} variant={EmptyStateVariant.sm}>
                        <EmptyStateBody>
                            Group level rules are not available for the <b>default</b> group.  Configure
                            global rules or else store artifacts in custom groups.
                        </EmptyStateBody>
                    </EmptyState>
                </div>
            </If>
            <If condition={props.group.groupId !== "default"}>
                <div className="group-rules">
                    <Card variant="secondary" style={{ backgroundColor: "white" }}>
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
                            <RuleList
                                type={RuleListType.Group}
                                rules={props.rules}
                                onEnableRule={props.onEnableRule}
                                onDisableRule={props.onDisableRule}
                                onConfigureRule={props.onConfigureRule}
                            />
                        </CardBody>
                    </Card>
                </div>
            </If>
        </div>
    );

};
