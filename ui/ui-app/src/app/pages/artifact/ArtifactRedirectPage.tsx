import { FunctionComponent } from "react";
import { Navigate, useParams } from "react-router-dom";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { useLoggerService } from "@services/useLoggerService.ts";


/**
 * Properties
 */
export type ArtifactRedirectPageProps = {
    // No properties.
}

/**
 * The artifact redirect page.
 */
//export class ArtifactRedirectPage extends PageComponent<ArtifactRedirectPageProps, ArtifactRedirectPageState> {
export const ArtifactRedirectPage: FunctionComponent<ArtifactRedirectPageProps> = () => {
    const params = useParams();
    const appNavigation: AppNavigation = useAppNavigation();
    const logger = useLoggerService();

    const groupId: string = params["groupId"] || "";
    const artifactId: any = params["artifactId"] || "";

    const redirect: string = appNavigation.createLink(`/artifacts/${ encodeURIComponent(groupId) }/${ encodeURIComponent(artifactId) }/versions/latest`);
    logger.info("[ArtifactRedirectPage] Redirecting to: %s", redirect);
    return (
        <Navigate to={redirect} replace />
    );
};
