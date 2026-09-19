import { VersionState, VersionStateObject } from "@sdk/lib/generated-client/models";


export const stateToLabel = (state: VersionState | undefined): string => {
    switch (state) {
        case VersionStateObject.ENABLED:
            return "Enabled";
        case VersionStateObject.DISABLED:
            return "Disabled";
        case VersionStateObject.DEPRECATED:
            return "Deprecated";
        case VersionStateObject.DRAFT:
            return "Draft";
        case VersionStateObject.SUNSET:
            return "Sunset";
        default:
            return "Unknown";
    }
};
