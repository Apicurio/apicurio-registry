import { GroupsService } from "./groups";
import { ConfigService } from "./config";
import { LoggerService } from "./logger";
import { AdminService } from "./admin";
import { Service } from "./baseService";
import { DownloaderService } from "./downloader";
import { AuthService } from "./auth";
import { UsersService } from "./users";
import { AlertsService } from "./alerts";

// TODO convert all of the services into React hooks

/**
 * Class that provides access to all of the services in the application.
 */
export class Services {

    static _isInit: boolean = false;

    public static getGroupsService(): GroupsService {
        return Services.all.groups;
    }

    public static getConfigService(): ConfigService {
        return Services.all.config;
    }

    public static getDownloaderService(): DownloaderService {
        return Services.all.downloader;
    }

    public static getLoggerService(): LoggerService {
        return Services.all.logger;
    }

    public static getAdminService(): AdminService {
        return Services.all.admin;
    }

    public static getAuthService(): AuthService {
        return Services.all.auth;
    }

    public static getUsersService(): UsersService {
        return Services.all.users;
    }

    public static getAlertsService(): AlertsService {
        return Services.all.alerts;
    }

    private static all: any = {
        groups: new GroupsService(),
        users: new UsersService(),
        config: new ConfigService(),
        downloader: new DownloaderService(),
        admin: new AdminService(),
        logger: new LoggerService(),
        auth: new AuthService(),
        alerts: new AlertsService(),
    };

    // tslint:disable-next-line:member-ordering member-access
    static _initialize(): void {
        console.info("[Services] _initialize() in Services");
        if (Services._isInit) {
            console.info("[Services] Services already initialized...skipping.");
            return;
        }
        console.info("[Services] Actually initializing Services!!!");
        // First perform simple service-service injection.
        Object.keys(Services.all).forEach( svcToInjectIntoName => {
            const svcToInjectInto: any = Services.all[svcToInjectIntoName];
            Object.keys(Services.all).filter(key => key !== svcToInjectIntoName).forEach(injectableSvcKey => {
                if (svcToInjectInto[injectableSvcKey] === undefined) {
                    svcToInjectInto[injectableSvcKey] = Services.all[injectableSvcKey];
                }
            });
        });
        // Once that's done, init() all the services
        Object.keys(Services.all).forEach( svcToInjectIntoName => {
            const svcToInit: Service = Services.all[svcToInjectIntoName];
            svcToInit.init();
        });
        Services._isInit = true;
        Services.getLoggerService().info("[Services] Services successfully initialized.");
    }

}
Services._initialize();
