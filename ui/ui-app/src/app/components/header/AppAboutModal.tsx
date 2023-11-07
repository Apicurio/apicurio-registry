import { FunctionComponent, useEffect, useState } from "react";
import { AboutModal, Text, TextContent, TextList, TextListItem, TextVariants } from "@patternfly/react-core";
import { VersionType } from "@services/version";
import { Services } from "@services/services.ts";
import { SystemInfo } from "@models/systemInfo.model.ts";
import { If } from "@app/components";
import "./AppAboutModal.css";


export type AppAboutModalProps = {
    isOpen: boolean;
    onClose: () => void;
};


export const AppAboutModal: FunctionComponent<AppAboutModalProps> = (props: AppAboutModalProps) => {
    const version: VersionType = Services.getVersionService().getVersion();
    const [info, setInfo] = useState<SystemInfo>();

    useEffect(() => {
        Services.getSystemService().getInfo().then(setInfo);
    }, []);

    return (
        <AboutModal
            className="registry-about"
            isOpen={props.isOpen}
            onClose={props.onClose}
            trademark="&copy; 2024 Red Hat"
            brandImageSrc="/apicurio_registry_logo_reverse.svg"
            brandImageAlt={version.name}
            aria-label={version.name}
        >
            <TextContent>
                <Text component={TextVariants.h2}>Web console info</Text>
                <TextList component="dl">
                    <TextListItem component="dt">Project</TextListItem>
                    <TextListItem component="dd"><a href={version.url} target="_blank">{ version.name }</a></TextListItem>

                    <TextListItem component="dt">Version</TextListItem>
                    <TextListItem component="dd">{ version.version }</TextListItem>

                    <TextListItem component="dt">Built on</TextListItem>
                    <TextListItem component="dd">{ "" + version.builtOn }</TextListItem>

                    <TextListItem component="dt">Digest</TextListItem>
                    <TextListItem component="dd">{ version.digest }</TextListItem>
                </TextList>
                <If condition={info !== undefined}>
                    <Text component={TextVariants.h2}>Registry API info</Text>
                    <TextList component="dl">
                        <TextListItem component="dt">Name</TextListItem>
                        <TextListItem component="dd">{ info?.name || "" }</TextListItem>

                        <TextListItem component="dt">Description</TextListItem>
                        <TextListItem component="dd">{ info?.description || "" }</TextListItem>

                        <TextListItem component="dt">Version</TextListItem>
                        <TextListItem component="dd">{ info?.version || "" }</TextListItem>

                        <TextListItem component="dt">Built on</TextListItem>
                        <TextListItem component="dd">{ info?.builtOn || "" }</TextListItem>
                    </TextList>
                </If>
            </TextContent>
        </AboutModal>
    );
};
