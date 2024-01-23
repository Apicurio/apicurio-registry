import React, { FunctionComponent } from "react";
import { If } from "@apicurio/common-ui-components";
import { Services } from "@services/services.ts";


export type RuleValueProps = {
    isGlobalRule: boolean;
    actions: React.ReactElement;
    label: React.ReactElement;
};

export const RuleValue: FunctionComponent<RuleValueProps> = (props: RuleValueProps) => {
    const readOnly: boolean = Services.getConfigService().featureReadOnly();
    const userIsAdmin: boolean = Services.getAuthService().isUserAdmin();
    const userIsDev: boolean = Services.getAuthService().isUserDeveloper();

    const isEditable: boolean = !readOnly && (props.isGlobalRule ? userIsAdmin : userIsDev);

    return (
        <>
            <If condition={isEditable}>
                { props.actions }
            </If>
            <If condition={!isEditable}>
                { props.label }
            </If>
        </>
    );

};
