import React, { FunctionComponent, useEffect, useState } from "react";
import "./RulesPage.css";
import { PageSection, PageSectionVariants, TextContent } from "@patternfly/react-core";
import { Rule } from "@models/rule.model.ts";
import { RootPageHeader, RuleList } from "@app/components";
import { Services } from "@services/services.ts";
import { PageDataLoader, PageError, PageErrorHandler, toPageError } from "@app/pages";


export type RulesPageProps = {
    // No properties.
}

/**
 * The global rules page.
 */
export const RulesPage: FunctionComponent<RulesPageProps> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [rules, setRules] = useState<Rule[]>([]);

    const createLoaders = (): Promise<any> => {
        return Services.getAdminService().getRules().then(setRules).catch(error => {
            setPageError(toPageError(error, "Error loading rules."));
        });
    };

    const doEnableRule = (ruleType: string): void => {
        Services.getLoggerService().debug("[RulesPage] Enabling global rule:", ruleType);
        let config: string = "FULL";
        if (ruleType === "COMPATIBILITY") {
            config = "BACKWARD";
        }
        Services.getAdminService().createRule(ruleType, config).catch(error => {
            setPageError(toPageError(error, `Error enabling "${ ruleType }" global rule.`));
        });
        setRules([...rules, { config, type: ruleType }]);
    };

    const doDisableRule = (ruleType: string): void => {
        Services.getLoggerService().debug("[RulesPage] Disabling global rule:", ruleType);
        Services.getAdminService().deleteRule(ruleType).catch(error => {
            setPageError(toPageError(error, `Error disabling "${ ruleType }" global rule.`));
        });
        setRules(rules.filter(r => r.type !== ruleType));
    };

    const doConfigureRule = (ruleType: string, config: string): void => {
        Services.getLoggerService().debug("[RulesPage] Configuring global rule:", ruleType, config);
        Services.getAdminService().updateRule(ruleType, config).catch(error => {
            setPageError(toPageError(error, `Error configuring "${ ruleType }" global rule.`));
        });
        setRules(rules.map(r => {
            if (r.type === ruleType) {
                return { config, type: r.type };
            } else {
                return r;
            }
        }));
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, []);

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection className="ps_rules-header" variant={PageSectionVariants.light} padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={1} />
                </PageSection>
                <PageSection className="ps_rules-description" variant={PageSectionVariants.light}>
                    <TextContent>
                        Manage the global rules for artifact content for this registry. Each global rule can be individually enabled, configured, and disabled.
                    </TextContent>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    <React.Fragment>
                        <RuleList
                            isGlobalRules={true}
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
