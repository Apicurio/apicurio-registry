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
import {ArtifactsService} from "./artifacts";
import {ConfigService} from "./config";
import {LoggerService} from "./logger";
import {GlobalsService} from "./globals";
import {Service} from "./baseService";
import {DownloaderService} from "./downloader";
import {AuthService} from "./auth";

interface AllServices {
    artifacts: ArtifactsService;
    config: ConfigService;
    globals: GlobalsService;
    logger: LoggerService;
    auth: AuthService;
}

/**
 * Class that provides access to all of the services in the application.
 */
export class Services {

    public static getArtifactsService(): ArtifactsService {
        return Services.all.artifacts;
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

    public static getGlobalsService(): GlobalsService {
        return Services.all.globals;
    }

    public static getAuthService() {
        return Services.all.auth;
    }

    private static all: any = {
        artifacts: new ArtifactsService(),
        config: new ConfigService(),
        downloader: new DownloaderService(),
        globals: new GlobalsService(),
        logger: new LoggerService(),
        auth: new AuthService()
    };

    // tslint:disable-next-line:member-ordering member-access
    static _intialize(): void {
        // First perform simple service-service injection.
        Object.keys(Services.all).forEach( svcToInjectIntoName => {
            const svcToInjectInto: any = Services.all[svcToInjectIntoName];
            Object.keys(Services.all).filter(key => key !== svcToInjectIntoName).forEach(injectableSvcKey => {
                if (svcToInjectInto[injectableSvcKey] !== undefined && svcToInjectInto[injectableSvcKey] === null) {
                    svcToInjectInto[injectableSvcKey] = Services.all[injectableSvcKey];
                }
            })
        });
        // Once that's done, init() all the services
        Object.keys(Services.all).forEach( svcToInjectIntoName => {
            const svcToInit: Service = Services.all[svcToInjectIntoName];
            svcToInit.init();
        });
    }

}
Services._intialize();
