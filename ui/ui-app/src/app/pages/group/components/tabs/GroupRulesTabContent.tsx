import {FunctionComponent} from "react";
import "./GroupInfoTabContent.css";
import "@app/styles/empty.css";
import { RuleList, RuleListType } from "@app/components";
import { Card, CardBody, CardTitle, Divider } from "@patternfly/react-core";
import { GroupMetaData, Rule } from "@sdk/lib/generated-client/models";

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
        <div className="group-tab-content">
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
        </div>
    );

};
