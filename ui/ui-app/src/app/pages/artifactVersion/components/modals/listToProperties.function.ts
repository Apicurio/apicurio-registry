import { ArtifactProperty } from "@app/pages";

export function listToProperties(properties: ArtifactProperty[]): { [key: string]: string|undefined } {
    const rval: { [key: string]: string|undefined } = {};
    properties.forEach(property => {
        if (property.name) {
            rval[property.name] = property.value;
        }
    });
    return rval;
}
