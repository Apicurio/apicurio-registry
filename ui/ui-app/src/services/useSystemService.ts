import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { getRegistryClient } from "@utils/rest.utils.ts";
import { SystemInfo } from "@sdk/lib/generated-client/models";
import { AuthService, useAuth } from "@apicurio/common-ui-components";


const getInfo = async (config: ConfigService, auth: AuthService): Promise<SystemInfo> => {
    console.info("[SystemService] Getting the global list of artifactTypes.");
    return getRegistryClient(config, auth).system.info.get().then(v => v!);
};


export interface SystemService {
    getInfo(): Promise<SystemInfo>;
}


export const useSystemService: () => SystemService = (): SystemService => {
    const config: ConfigService = useConfigService();
    const auth: AuthService = useAuth();

    return {
        getInfo(): Promise<SystemInfo> {
            return getInfo(config, auth);
        }
    };
};
