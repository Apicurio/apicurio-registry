export interface LoggerService {
    debug(message: any, ...optionalParams: any[]): void;
    info(message: any, ...optionalParams: any[]): void;
    warn(message: any, ...optionalParams: any[]): void;
    error(message: any, ...optionalParams: any[]): void;
}


export const useLoggerService: () => LoggerService = (): LoggerService => {
    return {
        debug(message: any, ...optionalParams: any[]): void {
            console.debug(message, ...optionalParams);
        },
        info(message: any, ...optionalParams: any[]): void {
            console.info(message, ...optionalParams);
        },
        warn(message: any, ...optionalParams: any[]): void {
            console.warn(message, ...optionalParams);
        },
        error(message: any, ...optionalParams: any[]): void {
            console.error(message, ...optionalParams);
        },
    };
};
