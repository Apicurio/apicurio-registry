import { FunctionComponent } from "react";
import { Button, ButtonVariant } from "@patternfly/react-core";
import { AdminService, useAdminService } from "@services/useAdminService.ts";
import { DownloadRef } from "@models/downloadRef.model.ts";


export type DownloadArtifactsProps = {
  fileName: string;
  downloadLinkLabel?: string;
};


export const DownloadArtifacts: FunctionComponent<DownloadArtifactsProps> = (props: DownloadArtifactsProps) => {
    const admin: AdminService = useAdminService();

    const generateDownloadLink = (fileName: string, href: string): void => {
        const link = document.createElement("a");
        link.href = href;
        link.download = `${fileName}.zip`;
        link.click();
    };

    const downloadArtifacts = () => {
        const { fileName } = props;
        admin.exportAs(fileName).then((response: DownloadRef) => {
            generateDownloadLink(fileName, response?.href);
        });
    };

    const { downloadLinkLabel = "Download artifacts (.zip)" } = props;

    return (
        <Button
            variant={ButtonVariant.link}
            onClick={downloadArtifacts}
            isInline
            isActive={true}
        >
            {downloadLinkLabel}
        </Button>
    );
};
