import React, { FunctionComponent, useEffect, useState } from "react";
import "./RulesPage.css";
import { PageSection, PageSectionVariants, TextContent } from "@patternfly/react-core";
import { Rule } from "@models/rule.model.ts";
import { RootPageHeader, RuleList } from "@app/components";
import { PageDataLoader, PageError, PageErrorHandler, toPageError } from "@app/pages";
import { AdminService, useAdminService } from "@services/useAdminService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";


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

    const admin: AdminService = useAdminService();
    const logger: LoggerService = useLoggerService();

    const createLoaders = (): Promise<any> => {
        return admin.getRules().then(setRules).catch(error => {
            setPageError(toPageError(error, "Error loading rules."));
        });
    };

    const doEnableRule = (ruleType: string): void => {
        logger.debug("[RulesPage] Enabling global rule:", ruleType);
        let config: string = "FULL";
        if (ruleType === "COMPATIBILITY") {
            config = "BACKWARD";
        }
        admin.createRule(ruleType, config).catch(error => {
            setPageError(toPageError(error, `Error enabling "${ ruleType }" global rule.`));
        });
        setRules([...rules, { config, type: ruleType }]);
    };

    const doDisableRule = (ruleType: string): void => {
        logger.debug("[RulesPage] Disabling global rule:", ruleType);
        admin.deleteRule(ruleType).catch(error => {
            setPageError(toPageError(error, `Error disabling "${ ruleType }" global rule.`));
        });
        setRules(rules.filter(r => r.type !== ruleType));
    };

    const doConfigureRule = (ruleType: string, config: string): void => {
        logger.debug("[RulesPage] Configuring global rule:", ruleType, config);
        admin.updateRule(ruleType, config).catch(error => {
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
