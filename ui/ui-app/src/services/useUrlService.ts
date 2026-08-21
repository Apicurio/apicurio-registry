import { createOptions, httpGet } from "@utils/rest.utils";
import { useConfigService, ConfigService } from "./useConfigService";

const githubRegex: RegExp = /^https:\/\/github\.com\/([^/]+)\/([^/]+)\/blob\/([^/]+)\/(.+)$/;

/**
 * The URL Service interface.
 */
export interface UrlService {
    fetchUrlContent(url: string): Promise<string>;
}

/**
 * React hook to get the URL service.
 */
export const useUrlService: () => UrlService = (): UrlService => {
    const config: ConfigService = useConfigService();

    const fetchUrlContent = async (url: string): Promise<string> => {
        const match: RegExpMatchArray | null = url.match(githubRegex);
        if (match !== null) {
            const org: string = match[1];
            const repo: string = match[2];
            const branch: string = match[3];
            const path: string = match[4];

            url = `https://raw.githubusercontent.com/${org}/${repo}/${branch}/${path}`;
        }

        console.info("[UrlService] Fetching content from a URL: ", url);

        const endpoint: string = url;
        const options: any = createOptions({
            "Accept": "*"
        });
        // Note: axios only checks maxContentLength against the response Content-Length header.
        // Responses sent with chunked transfer-encoding, or from servers that omit the header,
        // bypass this cap entirely.
        options.maxContentLength = config.featureUrlImportMaxContentLength();
        options.responseType = "text";
        options.transformResponse = (data: any) => data;
        return httpGet<string>(endpoint, options);
    };

    return {
        fetchUrlContent
    };
};
