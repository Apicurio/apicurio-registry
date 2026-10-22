export interface VersionType {
    name: string;
    version: string;
    digest: string;
    builtOn: string;
    url: string;
}

const DEFAULT_VERSION: VersionType = {
    name: "Apicurio Registry",
    version: "DEV",
    digest: "DEV",
    builtOn: new Date().toString(),
    url: "http://www.apicur.io/"
};


const getVersion = (): VersionType => {
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    if (ApicurioInfo) { return ApicurioInfo as VersionType; }

    const gw: any = window as any;
    if (gw["ApicurioInfo"]) {
        return gw["ApicurioInfo"] as VersionType;
    }

    return DEFAULT_VERSION;
};


export interface VersionService {
    getVersion(): VersionType;
}


export const useVersionService: () => VersionService = (): VersionService => {
    return {
        getVersion
    };
};
