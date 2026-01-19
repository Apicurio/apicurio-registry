import React, { FunctionComponent } from "react";
import { Label, Truncate } from "@patternfly/react-core";
import { SearchedArtifact, SearchedGroup, SearchedVersion } from "@sdk/lib/generated-client/models";
import { labelsToAny } from "@utils/rest.utils.ts";

export type ListedItemLabelsProps = {
    item: SearchedVersion | SearchedArtifact | SearchedGroup | undefined;
    onClick?: (key: string, value: string | undefined) => void;
};


export const ListedItemLabels: FunctionComponent<ListedItemLabelsProps> = (props: ListedItemLabelsProps) => {

    return (
        <React.Fragment>
            {
                Object.entries(labelsToAny(props.item?.labels)).map(([key, value]) =>
                    <Label
                        key={`label-${key}`}
                        color="purple"
                        style={{ marginBottom: "2px", marginRight: "5px", marginTop: "5px" }}
                        onClick={() => {
                            if (props.onClick) {
                                props.onClick(key, value as string | undefined);
                            }
                        }}
                    >
                        <Truncate
                            className="label-truncate"
                            content={`${key}=${value}`} />
                    </Label>
                )
            }
        </React.Fragment>
    );

};
