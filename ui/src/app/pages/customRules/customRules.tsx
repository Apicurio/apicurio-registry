/**
 * @license
 * Copyright 2021 Red Hat
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
import "./customRules.css";
import {PageSection, PageSectionVariants, TextContent} from '@patternfly/react-core';
import {PageComponent, PageProps, PageState} from "../basePage";
import {CustomRule, CustomRuleUpdate, Rule} from "../../../models";
import {Services} from "../../../services";
import {RootPageHeader} from "../../components";
import { CustomRuleList } from "src/app/components/customRuleList";
import { CustomRulesPageToolbar } from "./components/toolbar";
import { CustomRuleForm } from "src/app/components/customRuleForm";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface CustomRulesPageProps extends PageProps {

}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface CustomRulesPageState extends PageState {
    customRules: CustomRule[] | null;
    isCustomRuleModalOpen: boolean;
}

/**
 * The global rules page.
 */
export class CustomRulesPage extends PageComponent<CustomRulesPageProps, CustomRulesPageState> {

    constructor(props: Readonly<CustomRulesPageProps>) {
        super(props);
    }

    public renderPage(): React.ReactElement {
        return (
            <React.Fragment>
                <PageSection className="ps_rules-header" variant={PageSectionVariants.light} padding={{ default : "noPadding" }}>
                    <RootPageHeader tabKey={2} />
                </PageSection>
                <PageSection className="ps_rules-description" variant={PageSectionVariants.light}>
                    <TextContent>
                        Manage the list of custom rules for this registry. Custom rules can be created, deleted and individually configured. Later custom rules are enabled either globally or per artifact. Custom rules can be re-used accross multiple artifacts.
                    </TextContent>
                </PageSection>
                <PageSection variant={PageSectionVariants.light} padding={{default : "noPadding"}}>
                    <CustomRulesPageToolbar customRulesCount={this.customRules().length}
                                            onCreateNewCustomRule={this.doCustomRuleModalOpen}/>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    <CustomRuleList 
                        customRules={this.customRules()}
                        onDeleteCustomRule={this.doDeleteCustomRule}
                        onUpdateCustomRule={this.doUpdateCustomRule}/>
                </PageSection>
                <CustomRuleForm isOpen={this.state.isCustomRuleModalOpen} onSubmit={this.doSubmitCustomRuleForm} onClose={this.doCustomRuleModalClose}/>
            </React.Fragment>
        );
    }

    protected initializePageState(): CustomRulesPageState {
        return {
            customRules: null,
            isCustomRuleModalOpen: false
        };
    }

    // @ts-ignore
    protected createLoaders(): Promise {
        return this.loadCustomRules();
    };

    private loadCustomRules(): Promise<CustomRule[]> {
        return Services.getAdminService().getCustomRules()
            .then( rules => {
                this.setMultiState({
                    isLoading: false,
                    customRules: rules
                });
                return rules;
            });
    }

    private customRules(): CustomRule[] {
        return this.state.customRules ? this.state.customRules : [];
    };

    private doCustomRuleModalOpen = (): void => {
        this.setSingleState("isCustomRuleModalOpen", true);
    };

    private doCustomRuleModalClose = (): void => {
        this.setSingleState("isCustomRuleModalOpen", false);
    };

    private doSubmitCustomRuleForm = (customRuleFormData: CustomRule): void => {
        this.doCustomRuleModalClose();
        // this.pleaseWait(true);
        if (customRuleFormData !== null) {
            Services.getLoggerService().debug("[RulesPage] Creating new custom rule:", customRuleFormData.id);
            Services.getAdminService().createCustomRule(customRuleFormData).catch(error => {
                this.handleServerError(error, `Error creating "${ customRuleFormData.id }" new custom rule.`);
            });
            this.setSingleState("customRules", [...this.customRules(), customRuleFormData]);
        }
    };

    private doUpdateCustomRule = (ruleId: string, customRuleUpdate: CustomRuleUpdate): void => {
        Services.getLoggerService().debug("[RulesPage] Updating custom rule:", ruleId);
        Services.getAdminService().updateCustomRule(ruleId, customRuleUpdate)
        .catch(error => {
            this.handleServerError(error, `Error updating "${ ruleId }" custom rule.`);
        })
        .then(v => {
            this.loadCustomRules();
        });
    }

    private doDeleteCustomRule = (customRuleId: string): void => {
        Services.getLoggerService().debug("[RulesPage] Removing global custom rule:", customRuleId);
        Services.getAdminService().deleteCustomRule(customRuleId).catch(error => {
            this.handleServerError(error, `Error removing "${ customRuleId }" global custom rule.`);
        });
        this.setSingleState("customRules", this.customRules().filter(r => r.id !== customRuleId));
    };

}
