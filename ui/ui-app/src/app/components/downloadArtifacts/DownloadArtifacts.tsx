import { FunctionComponent } from "react";
import { Button, ButtonVariant } from "@patternfly/react-core";
import { Services } from "../../../services";


export type DownloadArtifactsProps = {
  fileName: string;
  downloadLinkLabel?: string;
};


export const DownloadArtifacts: FunctionComponent<DownloadArtifactsProps> = (props: DownloadArtifactsProps) => {

    const generateDownloadLink = (fileName: string, href: string): void => {
        const link = document.createElement("a");
        link.href = href;
        link.download = `${fileName}.zip`;
        link.click();
    };

    const downloadArtifacts = () => {
        const { fileName } = props;
        Services.getAdminService()
            .exportAs(fileName)
            .then((response) => {
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
