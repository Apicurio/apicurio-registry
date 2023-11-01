import { Service } from "../baseService";


function _window(): Window {
    return window;
}


/**
 * A simple logger service.
 */
export class DownloaderService implements Service {

    private window: Window | any;

    public init(): void {
        this.window = _window();
    }

    /**
     * Called to download some content as a file to the user's local filesystem.
     * @param content
     * @param contentType
     * @param filename
     */
    public downloadToFS(content: string, contentType: string, filename: string): Promise<boolean> {
        console.info("[DownloaderService] Downloading an API definition.");

        if (this.window.chrome !== undefined) {
            // Chrome version
            const link = document.createElement("a");
            const blob: Blob = new Blob([content], { type: contentType });
            link.href = window.URL.createObjectURL(blob);
            link.download = filename;
            link.click();
        } else if (window.navigator !== undefined && (<any>window.navigator).msSaveBlob !== undefined) {
            // IE version
            const blob: Blob = new Blob([content], { type: contentType });
            (<any>window.navigator).msSaveBlob(blob, filename);
        } else {
            // Firefox version
            const file: File = new File([content], filename, { type: "application/force-download" });
            window.open(URL.createObjectURL(file));
        }
        // Not async right now - so just resolve to true
        return Promise.resolve(true);
    }

    /**
     * Called to download base64 encoded content to the local filesystem.
     * @param content the base64 encoded data
     * @param filename name of the file to save as
     */
    public downloadBase64DataToFS(content: string, filename: string): Promise<boolean> {
        console.info("[DownloaderService] Downloading b64 content");
        const link = document.createElement("a");
        link.href = `data:text/plain;base64,${content}`;
        link.download = filename;
        link.click();

        // Not async right now - so just resolve to true
        return Promise.resolve(true);
    }
}
