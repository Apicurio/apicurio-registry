import { FunctionComponent } from "react";
import { Label, LabelGroup, Tooltip } from "@patternfly/react-core";
import { FederatedSearchSource } from "@sdk/lib/generated-client/models";
import { SOURCE_COLORS, describeSource, sourceLabel } from "./federatedSource";

export type FederatedSourcesProps = {
    sources: FederatedSearchSource[];
};

/**
 * The outcome of every registry a federated agent search asked: this one first, then each peer.
 */
export const FederatedSources: FunctionComponent<FederatedSourcesProps> = (props: FederatedSourcesProps) => {
    return (
        <LabelGroup categoryName="Registries searched" numLabels={props.sources.length}
            className="federated-sources" data-testid="federated-sources">
            {props.sources.map(source => (
                <Tooltip key={source.source} content={describeSource(source)}>
                    <Label color={SOURCE_COLORS[source.outcome || ""] || "grey"} isCompact
                        data-testid={`federated-source-${source.source}`}>
                        {sourceLabel(source)}
                    </Label>
                </Tooltip>
            ))}
        </LabelGroup>
    );
};
