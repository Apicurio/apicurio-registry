import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { createEndpoint, createOptions, httpGet } from "@utils/rest.utils.ts";
import { SystemInfo } from "@models/systemInfo.model.ts";


const getInfo = async (config: ConfigService): Promise<SystemInfo> => {
    console.info("[SystemService] Getting the global list of artifactTypes.");
    const baseHref: string = config.artifactsUrl();
    const options = createOptions({});
    const endpoint: string = createEndpoint(baseHref, "/system/info");
    return httpGet<SystemInfo>(endpoint, options);
};

export interface SystemService {
    getInfo(): Promise<SystemInfo>;
}


export const useSystemService: () => SystemService = (): SystemService => {
    const config: ConfigService = useConfigService();

    return {
        getInfo(): Promise<SystemInfo> {
            return getInfo(config);
        }
    };
};
