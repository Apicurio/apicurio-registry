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
        } else if (window.navigator !== undefined && window.navigator.msSaveBlob !== undefined) {
            // IE version
            const blob: Blob = new Blob([content], { type: contentType });
            window.navigator.msSaveBlob(blob, filename);
        } else {
            // Firefox version
            const file: File = new File([content], filename, { type: "application/force-download" });
            window.open(URL.createObjectURL(file));
        }
        // Not async right now - so just resolve to true
        return Promise.resolve(true);
    }
}
