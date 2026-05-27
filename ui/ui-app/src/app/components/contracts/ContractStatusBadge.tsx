import { FunctionComponent } from "react";
import { Label } from "@patternfly/react-core";

export type ContractStatusBadgeProps = {
    status: string | undefined;
};

export const ContractStatusBadge: FunctionComponent<ContractStatusBadgeProps> = ({ status }) => {
    if (!status) {
        return <Label color="grey">Unknown</Label>;
    }

    switch (status.toUpperCase()) {
        case "DRAFT":
            return <Label color="blue">Draft</Label>;
        case "STABLE":
            return <Label color="green">Stable</Label>;
        case "DEPRECATED":
            return <Label color="orange">Deprecated</Label>;
        default:
            return <Label color="grey">{status}</Label>;
    }
};
