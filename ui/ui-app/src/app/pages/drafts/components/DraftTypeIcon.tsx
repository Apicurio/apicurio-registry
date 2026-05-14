import React, { FunctionComponent } from "react";
import "./DraftTypeIcon.css";
import { ArtifactTypes } from "@services/useArtifactTypesService.ts";


const icon = (type: string | undefined): string => {
    switch (type) {
        case ArtifactTypes.AVRO:
            return "type-avro";
        case ArtifactTypes.PROTOBUF:
            return "type-protobuf";
        case ArtifactTypes.JSON:
            return "type-json";
        case ArtifactTypes.OPENAPI:
            return "type-openapi";
        case ArtifactTypes.ASYNCAPI:
            return "type-asyncapi";
        case ArtifactTypes.MODEL_SCHEMA:
            return "type-modelschema";
        case ArtifactTypes.PROMPT_TEMPLATE:
            return "type-prompttemplate";
    }
    return "";
};


/**
 * Properties
 */
export type DraftTypeIconProps = {
    className?: string;
    type: string;
    isShowIcon: boolean;
    isShowLabel: boolean;
}

export const DraftTypeIcon: FunctionComponent<DraftTypeIconProps> = ({ className, type, isShowIcon, isShowLabel }: DraftTypeIconProps) => {
    const getTitle = (): string => {
        let title: string = type;
        switch (type) {
            case ArtifactTypes.AVRO:
                title = "Avro Schema";
                break;
            case ArtifactTypes.PROTOBUF:
                title = "Protobuf Schema";
                break;
            case ArtifactTypes.JSON:
                title = "JSON Schema";
                break;
            case ArtifactTypes.OPENAPI:
                title = "OpenAPI Definition";
                break;
            case ArtifactTypes.ASYNCAPI:
                title = "AsyncAPI Definition";
                break;
            case ArtifactTypes.MODEL_SCHEMA:
                title = "AI Model Schema";
                break;
            case ArtifactTypes.PROMPT_TEMPLATE:
                title = "Prompt Template";
                break;
        }
        return title;
    };

    const getLabel = (): string => {
        let title: string = type;
        switch (type) {
            case ArtifactTypes.AVRO:
                title = "Avro";
                break;
            case ArtifactTypes.PROTOBUF:
                title = "Protobuf";
                break;
            case ArtifactTypes.JSON:
                title = "JSON schema";
                break;
            case ArtifactTypes.OPENAPI:
                title = "OpenAPI";
                break;
            case ArtifactTypes.ASYNCAPI:
                title = "AsyncAPI";
                break;
            case ArtifactTypes.MODEL_SCHEMA:
                title = "Model Schema";
                break;
            case ArtifactTypes.PROMPT_TEMPLATE:
                title = "Prompt Template";
                break;
        }
        return title;
    };

    const renderLabel = (): React.ReactNode | undefined => {
        if (isShowLabel) {
            return (
                <span>{getLabel()}</span>
            );
        } else {
            return undefined;
        }
    };

    const typeClass = (): string => {
        return icon(type);
    };

    return (
        <div className={ `draft-type-icon ${typeClass()} ${isShowIcon ? "show-icon" : ""} ${isShowLabel ? "show-label" : ""} ${className || ""}` }
            title={getTitle()} children={renderLabel() as any}  />
    );
};
