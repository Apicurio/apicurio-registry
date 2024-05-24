import { ArtifactLabel } from "@app/pages";

export function labelsToList(labels: { [key: string]: string|undefined }): ArtifactLabel[] {
    return Object.keys(labels).filter((key) => key !== undefined).map(key => {
        return {
            name: key,
            value: labels[key],
            nameValidated: "default",
            valueValidated: "default"
        };
    });
}
