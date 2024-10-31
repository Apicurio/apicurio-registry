import { FunctionComponent, useState } from "react";
import { ErrorTabContent } from "@app/pages";
import { If } from "@apicurio/common-ui-components";
import YAML from "yaml";
import { AsyncApiVisualizer, OpenApiVisualizer } from "@app/pages/version/components/tabs/visualizers";
import { ArtifactTypes } from "@services/useArtifactTypesService.ts";

enum VisualizerType {
    OPENAPI, ASYNCAPI, OTHER
}

const getVisualizerType = (artifactType: string): VisualizerType => {
    if (artifactType === ArtifactTypes.OPENAPI) {
        return VisualizerType.OPENAPI;
    }
    if (artifactType === ArtifactTypes.ASYNCAPI) {
        return VisualizerType.ASYNCAPI;
    }
    return VisualizerType.OTHER;
};

const parseContent = (artifactContent: string): any => {
    // Try as JSON
    try {
        return JSON.parse(artifactContent);
    } catch {
        // Do nothing
    }

    // Try as YAML
    try {
        return YAML.parse(artifactContent);
    } catch {
        // Do nothing
    }
    return {};
};

/**
 * Properties
 */
export type DocumentationTabContentProps = {
    versionContent: string;
    artifactType: string;
};


/**
 * Models the content of the Documentation tab on the artifact details page.
 */
export const DocumentationTabContent: FunctionComponent<DocumentationTabContentProps> = (props: DocumentationTabContentProps) => {
    const [parsedContent] = useState(parseContent(props.versionContent));
    const [visualizerType] = useState(getVisualizerType(props.artifactType));
    const [error] = useState<any>();

    const isError = () : boolean => {
        return !!error;
    };

    if (isError()){
        return <ErrorTabContent error={{ errorMessage: "Artifact isn't a valid OpenAPI structure", error: error }}/>;
    }

    return (
        <>
            <If condition={visualizerType === VisualizerType.OPENAPI}>
                <OpenApiVisualizer spec={parsedContent} />
            </If>
            <If condition={visualizerType === VisualizerType.ASYNCAPI}>
                <AsyncApiVisualizer spec={parsedContent} />
            </If>
            <If condition={visualizerType === VisualizerType.OTHER}>
                <h1>Unsupported Type: { props.artifactType }</h1>
            </If>
        </>
    );

};
