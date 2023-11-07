import { BaseService } from "../baseService";
import { SystemInfo } from "@models/systemInfo.model.ts";

/**
 * The System service.
 */
export class SystemService extends BaseService {

    public getInfo(): Promise<SystemInfo> {
        this.logger?.info("[SystemService] Getting the global list of artifactTypes.");
        const endpoint: string = this.endpoint("/system/info");
        return this.httpGet<SystemInfo>(endpoint);
    }

}
