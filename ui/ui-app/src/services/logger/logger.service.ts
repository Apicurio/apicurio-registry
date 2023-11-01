import { Service } from "../baseService";
import { ConfigService } from "../config";

/**
 * A simple logger service.
 */
export class LoggerService implements Service {
    protected config: ConfigService | undefined;

    public init(): void {
        // Nothing to init
    }

    public debug(message: any, ...optionalParams: any[]): void {
        console.debug(message, ...optionalParams);
    }

    public info(message: any, ...optionalParams: any[]): void {
        console.info(message, ...optionalParams);
    }

    public warn(message: any, ...optionalParams: any[]): void {
        console.warn(message, ...optionalParams);
    }

    public error(message: any, ...optionalParams: any[]): void {
        console.error(message, ...optionalParams);
    }

}
