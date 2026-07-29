import React, { FunctionComponent, useEffect, useState } from "react";
import "./RulesPage.css";
import { Alert, AlertActionCloseButton, PageSection, PageSectionVariants, Content } from "@patternfly/react-core";
import { RootPageHeader, RuleList, RuleListType } from "@app/components";
import { PageDataLoader, PageError, PageErrorHandler, PageProperties, RULES_PAGE_IDX, toPageError } from "@app/pages";
import { AdminService, useAdminService } from "@services/useAdminService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { Rule, RuleType } from "@sdk/lib/generated-client/models";


/**
 * The global rules page.
 */
export const RulesPage: FunctionComponent<PageProperties> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [rules, setRules] = useState<Rule[]>([]);
    const [ruleActionError, setRuleActionError] = useState<string>();

    const admin: AdminService = useAdminService();
    const logger: LoggerService = useLoggerService();

    const createLoaders = (): Promise<any> => {
        return admin.getRules().then(setRules).catch(error => {
            setPageError(toPageError(error, "Error loading rules."));
        });
    };

    const doEnableRule = (ruleType: string): void => {
        logger.debug("[RulesPage] Enabling global rule:", ruleType);
        setRuleActionError(undefined);
        let config: string = "FULL";
        if (ruleType === "COMPATIBILITY") {
            config = "BACKWARD";
        }
        admin.createRule(ruleType, config).then(() => {
            setRules([...rules, { config, ruleType: ruleType as RuleType }]);
        }).catch(error => {
            setRuleActionError(error?.message || `Error enabling "${ ruleType }" global rule. Please try again.`);
        });
    };

    const doDisableRule = (ruleType: string): void => {
        logger.debug("[RulesPage] Disabling global rule:", ruleType);
        setRuleActionError(undefined);
        admin.deleteRule(ruleType).then(() => {
            setRules(rules.filter(r => r.ruleType !== ruleType));
        }).catch(error => {
            setRuleActionError(error?.message || `Error disabling "${ ruleType }" global rule. Please try again.`);
        });
    };

    const doConfigureRule = (ruleType: string, config: string): void => {
        logger.debug("[RulesPage] Configuring global rule:", ruleType, config);
        setRuleActionError(undefined);
        admin.updateRule(ruleType, config).then(() => {
            setRules(rules.map(r => {
                if (r.ruleType === ruleType) {
                    return { config, ruleType: r.ruleType };
                } else {
                    return r;
                }
            }));
        }).catch(error => {
            setRuleActionError(error?.message || `Error configuring "${ ruleType }" global rule. Please try again.`);
        });
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, []);

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection hasBodyWrapper={false} className="ps_rules-header"  padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={RULES_PAGE_IDX} />
                </PageSection>
                <PageSection hasBodyWrapper={false} className="ps_rules-description" >
                    <Content>
                        Manage the global rules for artifact content for this registry. Each global rule can be individually enabled, configured, and disabled.
                    </Content>
                </PageSection>
                <PageSection hasBodyWrapper={false} variant={PageSectionVariants.default} isFilled={true}>
                    <React.Fragment>
                        {ruleActionError && (
                            <Alert
                                variant="danger"
                                title={ruleActionError}
                                actionClose={<AlertActionCloseButton onClose={() => setRuleActionError(undefined)} />}
                                isInline
                                style={{ marginBottom: "15px" }}
                                data-testid="rule-action-error" />
                        )}
                        <RuleList
                            type={RuleListType.Global}
                            rules={rules}
                            onEnableRule={doEnableRule}
                            onDisableRule={doDisableRule}
                            onConfigureRule={doConfigureRule} />
                    </React.Fragment>
                </PageSection>
            </PageDataLoader>
        </PageErrorHandler>
    );

};
