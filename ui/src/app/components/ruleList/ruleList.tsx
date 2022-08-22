/**
 * @license
 * Copyright 2020 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from "react";
import "./ruleList.css";
import {
    Button,
    DataList,
    DataListAction,
    DataListCell,
    DataListItem,
    DataListItemCells,
    DataListItemRow
} from "@patternfly/react-core";
import { PureComponent, PureComponentProps, PureComponentState } from "../baseComponent";
import { CodeBranchIcon, OkIcon, TrashIcon } from "@patternfly/react-icons";
import { CompatibilityDropdown } from "./compatibility-dropdown";
import { ValidityDropdown } from "./validity-dropdown";
import { IfFeature } from "../common/ifFeature";
import { IfAuth } from "../common";
import { Rule } from "../../../models";


export interface RuleListProps extends PureComponentProps {
    onEnableRule: (ruleType: string) => void;
    onDisableRule: (ruleType: string) => void;
    onConfigureRule: (ruleType: string, config: string) => void;
    rules: Rule[];
}

// tslint:disable-next-line:no-empty-interface
export interface RuleListState extends PureComponentState {
}


export class RuleList extends PureComponent<RuleListProps, RuleListState> {

    constructor(props: Readonly<RuleListProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        let validityRuleActions: React.ReactElement = (
            <Button variant="secondary"
                    key="enable-action"
                    data-testid="rules-validity-enable"
                    onClick={this.doEnableRule("VALIDITY")}>Enable</Button>
        );
        if (this.isRuleEnabled("VALIDITY")) {
            validityRuleActions = (
                <React.Fragment>
                    <ValidityDropdown value={this.getRuleConfig("VALIDITY")}
                                      onSelect={this.doConfigureRule("VALIDITY")} />
                    <Button variant="plain"
                            key="delete-action"
                            data-testid="rules-validity-disable"
                            title="Disable the validity rule"
                            onClick={this.doDisableRule("VALIDITY")}><TrashIcon /></Button>
                </React.Fragment>
            );
        }
        let compatibilityRuleActions: React.ReactElement = (
            <Button variant="secondary"
                    key="enable-action"
                    data-testid="rules-compatibility-enable"
                    onClick={this.doEnableRule("COMPATIBILITY")}>Enable</Button>
        );
        if (this.isRuleEnabled("COMPATIBILITY")) {
            compatibilityRuleActions = (
                <React.Fragment>
                    <CompatibilityDropdown value={this.getRuleConfig("COMPATIBILITY")}
                                           onSelect={this.doConfigureRule("COMPATIBILITY")} />
                    <Button variant="plain"
                            key="delete-action"
                            data-testid="rules-compatibility-disable"
                            title="Disable the compatibility rule"
                            onClick={this.doDisableRule("COMPATIBILITY")}><TrashIcon /></Button>
                </React.Fragment>
            );
        }

        return (
            <DataList aria-label="Artifact rules">
                <DataListItem aria-labelledby="validity-rule-name">
                    <DataListItemRow className={this.getRuleRowClasses("VALIDITY")}>
                        <DataListItemCells dataListCells={[
                            <DataListCell key="rule-name">
                                <OkIcon className="rule-icon" />
                                <span id="validity-rule-name">Validity rule</span>
                            </DataListCell>,
                            <DataListCell key="rule-description">Ensure that content is <em>valid</em> when updating this artifact.</DataListCell>
                        ]}
                        />
                        <IfAuth isDeveloper={true}>
                            <IfFeature feature="readOnly" isNot={true}>
                                <DataListAction
                                    aria-labelledby="selectable-action-item1 selectable-action-action1"
                                    id="selectable-action-action1"
                                    aria-label="Actions"
                                >
                                    { validityRuleActions}
                                </DataListAction>
                            </IfFeature>
                        </IfAuth>
                    </DataListItemRow>
                </DataListItem>
                <DataListItem aria-labelledby="compatibility-rule-name">
                    <DataListItemRow className={this.getRuleRowClasses("COMPATIBILITY")}>
                        <DataListItemCells dataListCells={[
                            <DataListCell key="rule-name">
                                <CodeBranchIcon className="rule-icon" />
                                <span id="compatibility-rule-name">Compatibility rule</span>
                            </DataListCell>,
                            <DataListCell key="rule-description">Enforce a compatibility level when updating this artifact (for example, Backwards Compatibility).</DataListCell>
                        ]}
                        />
                        <IfAuth isDeveloper={true}>
                            <IfFeature feature="readOnly" isNot={true}>
                                <DataListAction
                                    aria-labelledby="selectable-action-item1 selectable-action-action1"
                                    id="selectable-action-action2"
                                    aria-label="Actions"
                                >
                                    { compatibilityRuleActions }
                                </DataListAction>
                            </IfFeature>
                        </IfAuth>
                    </DataListItemRow>
                </DataListItem>
            </DataList>
        );
    }

    protected initializeState(): RuleListState {
        return {};
    }

    private isRuleEnabled(ruleType: string): boolean {
        return this.props.rules.filter(rule => rule.type === ruleType).length > 0;
    }

    private getRuleRowClasses(ruleType: string): string {
        const classes: string[] = [ "rule" ];
        if (ruleType === "COMPATIBILITY") {
            classes.push("compatibility-rule");
        } else {
            classes.push("validity-rule");
        }
        if (!this.isRuleEnabled(ruleType)) {
            classes.push("disabled-state-text");
        }
        return classes.join(' ');
    }

    private getRuleConfig(ruleType: string): string {
        const frules: Rule[] = this.props.rules.filter(r => r.type === ruleType);
        if (frules.length === 1) {
            return frules[0].config;
        } else {
            return "UNKNOWN";
        }
    };

    private doEnableRule = (ruleType: string): (() => void) => {
        return () => {
            this.props.onEnableRule(ruleType);
        };
    };

    private doDisableRule = (ruleType: string): (() => void) => {
        return () => {
            this.props.onDisableRule(ruleType);
        };
    };

    private doConfigureRule = (ruleType: string): ((config: string) => void) => {
        return (config: string) => {
            this.props.onConfigureRule(ruleType, config);
        };
    };

}
