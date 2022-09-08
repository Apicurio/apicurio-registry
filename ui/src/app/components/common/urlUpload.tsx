import React, { FunctionComponent, useState } from "react";
import "./urlUpload.css";
import { Button, Spinner, TextArea, TextInput } from "@patternfly/react-core";
import { UrlService, useUrlService } from "../../../services";
import { If } from "./if";
import { IsLoading } from "./isLoading";

/**
 * Properties
 */
export type UrlUploadProps = {
    id: string|"url-upload";
    urlPlaceholder: string|"";
    onChange: (value: string|undefined, url: string|undefined) => void;
};

/**
 * A control similar to the FileUpload control from patternfly that allows uploading from
 * a URL instead of a file.
 */
export const UrlUpload: FunctionComponent<UrlUploadProps> = ({ id, urlPlaceholder, onChange }: UrlUploadProps) => {
    const [url, setUrl] = useState<string>();
    const [previewContent, setPreviewContent] = useState<string>();
    const [isLoading, setLoading] = useState<boolean>(false);
    const [downloadError, setDownloadError] = useState<string>();

    const urlService: UrlService = useUrlService();

    const onTextInputChange = (value: string): void => {
        setUrl(value);
    };

    const hasUrl = (): boolean => {
        return url != undefined && url.trim().length > 0;
    };

    const hasError = (): boolean => {
        return downloadError != undefined && downloadError.trim().length > 0;
    };

    const onFetch = (): void => {
        setLoading(true);
        urlService.fetchUrlContent(url as string).then(content => {
            setDownloadError(undefined);
            setPreviewContent(content);
            setLoading(false);
            onChange(content, url);
        }).catch(error => {
            setDownloadError(error.message);
            setLoading(false);
        });
    };

    const onClear = (): void => {
        setUrl("");
        setPreviewContent("");
        onChange(undefined, undefined);
    };

    const spinner: React.ReactNode = (
        <div className="url-upload-loading">
            <Spinner size="md" className="spinner" />
            <span className="spinner-message">Loading URL content</span>
        </div>
    );

    return (
        <div className="url-upload">
            <div className="url-upload-flex">
                <div className="url-upload-url">
                    <TextInput data-testid={`${id}-input`} value={url} type="text" placeholder={urlPlaceholder} id={id}
                        onChange={onTextInputChange} aria-label="url input" />
                </div>
                <div className="url-fetch-button">
                    <Button data-testid={`${id}-fetch`} variant="control" isDisabled={!hasUrl()} onClick={onFetch}>Fetch</Button>
                </div>
                <div className="url-clear-button">
                    <Button data-testid={`${id}-clear`} variant="control" isDisabled={!hasUrl()} onClick={onClear}>Clear</Button>
                </div>
            </div>
            <div className="url-upload-preview">
                <IsLoading condition={isLoading} loadingComponent={spinner}>
                    <If condition={hasError}>
                        <div className="url-upload-error">
                            <div>
                                Error getting content from URL.
                            </div>
                            <div>
                                {downloadError}
                            </div>
                        </div>
                    </If>
                    <If condition={!hasError()}>
                        <TextArea value={previewContent} isReadOnly={true}></TextArea>
                    </If>
                </IsLoading>
            </div>
        </div>
    );
};
