
/**
 * Downloads the given content to the filesystem using the given content type and
 * file name.
 * @param content
 * @param contentType
 * @param filename
 */
async function downloadToFS(content: string, contentType: string, filename: string): Promise<void> {
    console.info("[DownloadService] Downloading a design.");
    const _w: any = window;

    if (_w.chrome !== undefined) {
        // Chrome version
        const link = document.createElement("a");
        const blob = new Blob([content], { type: contentType });
        link.href = _w.URL.createObjectURL(blob);
        link.download = filename;
        link.click();
    } else if (_w.navigator !== undefined && _w.navigator.msSaveBlob !== undefined) {
        // IE version
        const blob = new Blob([content], { type: contentType });
        _w.navigator.msSaveBlob(blob, filename);
    } else {
        // Firefox version
        const file = new File([content], filename, { type: "application/force-download" });
        _w.open(URL.createObjectURL(file));
    }

    return Promise.resolve();
}

/**
 * Called to download base64 encoded content to the local filesystem.
 * @param content the base64 encoded data
 * @param filename name of the file to save as
 */
async function downloadBase64DataToFS(content: string, filename: string): Promise<boolean> {
    console.info("[DownloaderService] Downloading b64 content");
    const link = document.createElement("a");
    link.href = `data:text/plain;base64,${content}`;
    link.download = filename;
    link.click();

    // Not async right now - so just resolve to true
    return Promise.resolve(true);
}

/**
 * The Download Service interface.
 */
export interface DownloadService {
    downloadToFS(content: string, contentType: string, filename: string): Promise<void>;
    downloadBase64DataToFS(content: string, filename: string): Promise<boolean>;
}


/**
 * React hook to get the Download service.
 */
export const useDownloadService: () => DownloadService = (): DownloadService => {
    return {
        downloadToFS,
        downloadBase64DataToFS
    };
};
