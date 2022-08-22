/**
 * @license
 * Copyright 2020 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { GroupsService } from "./groups";
import { ConfigService } from "./config";
import { LoggerService } from "./logger";
import { AdminService } from "./admin";
import { Service } from "./baseService";
import { DownloaderService } from "./downloader";
import { AuthService } from "./auth";
import { UsersService } from "./users";
import { AlertsService } from "./alerts";

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
    static _intialize(): void {
        console.info("[Services] _initialize() in Services");
        if (Services._isInit) {
            console.info("[Services] Services already intialized...skipping.");
            return;
        }
        console.info("[Services] Actually initializing Services!!!");
        // First perform simple service-service injection.
        Object.keys(Services.all).forEach( svcToInjectIntoName => {
            const svcToInjectInto: any = Services.all[svcToInjectIntoName];
            Object.keys(Services.all).filter(key => key !== svcToInjectIntoName).forEach(injectableSvcKey => {
                if (svcToInjectInto[injectableSvcKey] !== undefined && svcToInjectInto[injectableSvcKey] === null) {
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
Services._intialize();
