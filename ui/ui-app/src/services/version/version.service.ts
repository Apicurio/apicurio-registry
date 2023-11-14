import { VersionType } from "@services/version/version.type.ts";
import { Service } from "@services/baseService.ts";

const DEFAULT_VERSION: VersionType = {
    name: "Apicurio Registry",
    version: "DEV",
    digest: "DEV",
    builtOn: new Date().toString(),
    url: "http://www.apicur.io/"
};


export function getVersion(): VersionType {
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    if (ApicurioInfo) { return ApicurioInfo as VersionType; }

    const gw: any = window as any;
    if (gw["ApicurioInfo"]) {
        return gw["ApicurioInfo"] as VersionType;
    }

    return DEFAULT_VERSION;
}


/**
 * A simple Version service.  Reads information from a global "ApicurioInfo" variable
 * that is typically included via JSONP.
 */
export class VersionService implements Service {
    private version: VersionType;

    constructor() {
        this.version = getVersion();
    }

    public init(): void {
        // Nothing to init (done in c'tor)
    }

    public updateConfig(version: VersionType): void {
        this.version = version;
    }

    public getVersion(): VersionType {
        return this.version;
    }

}
