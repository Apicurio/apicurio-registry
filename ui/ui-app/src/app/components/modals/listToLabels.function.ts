import { ArtifactLabel } from "@app/pages";

export function listToLabels(labels: ArtifactLabel[]): { [key: string]: string|undefined } {
    const rval: { [key: string]: string|undefined } = {};
    labels.forEach(label => {
        if (label.name) {
            rval[label.name] = label.value;
        }
    });
    return rval;
}
