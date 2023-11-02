import { FunctionComponent } from "react";
import "./ArtifactList.css";

/**
 * Properties
 */
export type ArtifactGroupProps = {
    groupId: string|null;
    onClick: (groupId: string) => void;
};


/**
 * Models the list of artifacts.
 */
export const ArtifactGroup: FunctionComponent<ArtifactGroupProps> = (props: ArtifactGroupProps) => {

    const style = (): string => {
        return !props.groupId ? "nogroup" : "group";
    };

    const fireOnClick = (): void => {
        props.onClick(props.groupId as string);
    };

    return (
        <a className={style()} onClick={fireOnClick}>{props.groupId}</a>
    );

};
