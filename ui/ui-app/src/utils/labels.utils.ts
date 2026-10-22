import { Labels } from "@sdk/lib/generated-client/models";
import { ArtifactLabel } from "@app/components";
import { labelsToAny } from "@utils/rest.utils.ts";

export const labelsToList = (labels: Labels | undefined): ArtifactLabel[] => {
    const theLabels: any = labelsToAny(labels);
    delete theLabels["additionalData"];
    return Object.keys(theLabels).filter((key) => key !== undefined).map(key => {
        return {
            name: key,
            value: theLabels[key] as string,
            nameValidated: "default",
            valueValidated: "default"
        };
    });
};


export const listToLabels = (labels: ArtifactLabel[]): Labels => {
    const rval: Labels = {
        additionalData: {}
    };
    labels.forEach(label => {
        if (label.name) {
            rval.additionalData![label.name] = label.value;
        }
    });
    return rval;
};
