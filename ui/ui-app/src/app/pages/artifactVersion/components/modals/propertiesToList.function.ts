import { ArtifactProperty } from "@app/pages";

export function propertiesToList(properties: { [key: string]: string|undefined }): ArtifactProperty[] {
    return Object.keys(properties).filter((key) => key !== undefined).map(key => {
        return {
            name: key,
            value: properties[key],
            nameValidated: "default",
            valueValidated: "default"
        };
    });
}
