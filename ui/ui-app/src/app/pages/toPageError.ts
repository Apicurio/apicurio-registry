import { PageError } from "@app/pages/PageError.ts";
import { Services } from "@services/services.ts";
import { PageErrorType } from "@app/pages/PageErrorType.ts";

export const toPageError = (error: any, errorMessage: any): PageError => {
    Services.getLoggerService().error("[PageDataLoader] Handling an error loading page data.");
    Services.getLoggerService().error("[PageDataLoader] ", errorMessage);
    Services.getLoggerService().error("[PageDataLoader] ", error);
    return {
        error,
        errorMessage,
        type: PageErrorType.Server
    };
};
