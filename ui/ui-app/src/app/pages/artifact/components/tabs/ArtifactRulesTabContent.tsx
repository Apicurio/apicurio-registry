import { FunctionComponent } from "react";
import "./ArtifactRulesTabContent.css";
import "@app/styles/empty.css";
import { RuleList, RuleListType } from "@app/components";
import { Card, CardBody, CardTitle, Divider } from "@patternfly/react-core";
import { ArtifactMetaData, Rule } from "@sdk/lib/generated-client/models";

/**
 * Properties
 */
export type ArtifactRulesTabContentProps = {
    artifact: ArtifactMetaData;
    rules: Rule[];
    onEnableRule: (ruleType: string) => void;
    onDisableRule: (ruleType: string) => void;
    onConfigureRule: (ruleType: string, config: string) => void;
};

/**
 * Models the content of the Artifact Rules tab.
 */
export const ArtifactRulesTabContent: FunctionComponent<ArtifactRulesTabContentProps> = (props: ArtifactRulesTabContentProps) => {

    return (
        <div className="artifact-rules-tab-content">
            <div className="artifact-rules">
                <Card>
                    <CardTitle>
                        <div className="rules-label">Artifact-specific rules</div>
                    </CardTitle>
                    <Divider />
                    <CardBody>
                        <p style={{ paddingBottom: "15px" }}>
                            Manage the content rules for this artifact. Each artifact-specific rule can be
                            individually enabled, configured, and disabled. Artifact-specific rules override
                            the equivalent global rules.
                        </p>
                        <RuleList
                            type={RuleListType.Artifact}
                            resourceOwner={props.artifact.owner}
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
