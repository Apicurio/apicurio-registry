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
import "./rules.css";
import {Flex, FlexItem, PageSection, PageSectionVariants, Spinner, TextContent} from '@patternfly/react-core';
import {PageComponent, PageProps, PageState} from "../basePage";
import {RulesPageHeader} from "./components/pageheader";
import {RuleList} from "../../components/ruleList";
import {Services} from "@apicurio/registry-services";
import {Rule} from "@apicurio/registry-models";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface RulesPageProps extends PageProps {

}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface RulesPageState extends PageState {
    rules: Rule[] | null;
}

/**
 * The global rules page.
 */
export class RulesPage extends PageComponent<RulesPageProps, RulesPageState> {

    constructor(props: Readonly<RulesPageProps>) {
        super(props);
    }

    public renderPage(): React.ReactElement {
        return (
            <React.Fragment>
                <PageSection className="ps_rules-header" variant={PageSectionVariants.light}>
                    <RulesPageHeader />
                </PageSection>
                <PageSection className="ps_rules-description" variant={PageSectionVariants.light}>
                    <TextContent>
                        Manage the list of Global Rules configured for the Registry below.  Rules can be enabled/disabled
                        and also individually configured.
                    </TextContent>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    {
                        this.isLoading() ?
                            <Flex>
                                <FlexItem><Spinner size="lg"/></FlexItem>
                                <FlexItem><span>Loading, please wait...</span></FlexItem>
                            </Flex>
                            :
                            <React.Fragment>
                                <RuleList rules={this.rules()}
                                          onEnableRule={this.doEnableRule}
                                          onDisableRule={this.doDisableRule}
                                          onConfigureRule={this.doConfigureRule} />
                            </React.Fragment>
                    }
                </PageSection>
            </React.Fragment>
        );
    }

    protected initializeState(): RulesPageState {
        return {
            isLoading: true,
            rules: null
        };
    }

    protected loadPageData(): void {
        Services.getGlobalsService().getRules().then( rules => {
            this.setMultiState({
                isLoading: false,
                rules
            });
        });
    }

    private rules(): Rule[] {
        if (this.state.rules) {
            return this.state.rules;
        } else {
            return [];
        }
    }

    private doEnableRule = (ruleType: string): void => {
        Services.getLoggerService().debug("[RulesPage] Enabling global rule:", ruleType);
        let config: string = "FULL";
        if (ruleType === "COMPATIBILITY") {
            config = "BACKWARD";
        }
        Services.getGlobalsService().createRule(ruleType, config).catch(error => {
            this.handleServerError(error, `Error enabling "${ ruleType }" global rule.`);
        });
        this.setSingleState("rules", [...this.rules(), {config, type: ruleType}]);
    };

    private doDisableRule = (ruleType: string): void => {
        Services.getLoggerService().debug("[RulesPage] Disabling global rule:", ruleType);
        Services.getGlobalsService().deleteRule(ruleType).catch(error => {
            this.handleServerError(error, `Error disabling "${ ruleType }" global rule.`);
        });
        this.setSingleState("rules", this.rules().filter(r => r.type !== ruleType));
    };

    private doConfigureRule = (ruleType: string, config: string): void => {
        Services.getLoggerService().debug("[RulesPage] Configuring global rule:", ruleType, config);
        Services.getGlobalsService().updateRule(ruleType, config).catch(error => {
            this.handleServerError(error, `Error configuring "${ ruleType }" global rule.`);
        });
        this.setSingleState("rules", this.rules().map(r => {
            if (r.type === ruleType) {
                return {config, type: r.type};
            } else {
                return r;
            }
        }));
    };

}
