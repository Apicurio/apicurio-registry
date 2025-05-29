import React, { FunctionComponent } from "react";
import { If } from "@apicurio/common-ui-components";
import { useUserService } from "@services/useUserService.ts";
import { useConfigService } from "@services/useConfigService.ts";
import { RuleListType } from "@app/components";


export type RuleValueProps = {
    type: RuleListType;
    actions: React.ReactElement;
    label: React.ReactElement;
    resourceOwner?: string | null;
};

export const RuleValue: FunctionComponent<RuleValueProps> = (props: RuleValueProps) => {
    const config = useConfigService();
    const user = useUserService();
    const readOnly: boolean = config.featureReadOnly();
    const userIsAdmin: boolean = user.isUserAdmin();
    const userIsDev: boolean = user.isUserDeveloper(props.resourceOwner);

    const isEditable: boolean = !readOnly && (props.type === RuleListType.Global ? userIsAdmin : userIsDev);

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
