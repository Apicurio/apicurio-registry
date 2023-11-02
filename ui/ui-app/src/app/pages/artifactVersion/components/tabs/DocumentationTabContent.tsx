import { FunctionComponent, useState } from "react";
import "./DocumentationTabContent.css";
import { ErrorTabContent } from "@app/pages";


/**
 * Properties
 */
export type DocumentationTabContentProps = {
    artifactContent: string;
    artifactType: string;
};


/**
 * Models the content of the Documentation tab on the artifact details page.
 */
export const DocumentationTabContent: FunctionComponent<DocumentationTabContentProps> = (props: DocumentationTabContentProps) => {
    const [error] = useState<any>();

    const isError = () : boolean => {
        return !!error;
    };

    // protected initializeState(): DocumentationTabContentState {
    //     try {
    //         return {
    //             parsedContent: JSON.parse(this.props.artifactContent),
    //             error: undefined
    //         };
    //     } catch(ex) {
    //         Services.getLoggerService().warn("Failed to parse content:");
    //         Services.getLoggerService().error(ex);
    //         return {
    //             parsedContent: undefined,
    //             error: ex
    //         };
    //     }
    // }

    if (isError()){
        return <ErrorTabContent error={{ errorMessage: "Artifact isn't a valid OpenAPI structure", error: error }}/>;
    }

    // let visualizer: React.ReactElement | null = null;
    // if (this.props.artifactType === "OPENAPI") {
    //     visualizer = <RedocStandalone spec={this.state.parsedContent} />;
    // }
    //
    // if(this.props.artifactType === "ASYNCAPI") {
    //     const config: ConfigInterface = {
    //         show: {
    //             sidebar: false
    //         }
    //     };
    //     visualizer = <AsyncApiComponent schema={this.state.parsedContent} config={config} />;
    // }
    //
    // if (visualizer !== null) {
    //     return visualizer;
    // } else {
    //     return <h1>Unsupported Type: { this.props.artifactType }</h1>;
    // }

    return <h1>Not yet implemented for type: { props.artifactType }</h1>;

};
