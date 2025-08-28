import { FunctionComponent, useEffect, useState } from "react";
import "./DraftsPage.css";
import { PageSection, PageSectionVariants, TextContent } from "@patternfly/react-core";
import { RootPageHeader } from "@app/components";
import { DRAFTS_PAGE_IDX, PageDataLoader, PageError, PageErrorHandler, PageProperties } from "@app/pages";

/**
 * The drafts page.
 */
export const DraftsPage: FunctionComponent<PageProperties> = () => {
    const [pageError, /*setPageError*/] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();

    const createLoaders = async (): Promise<any> => {
        return [];
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, []);

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection className="ps_drafts-header" variant={PageSectionVariants.light} padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={DRAFTS_PAGE_IDX} />
                </PageSection>
                <PageSection className="ps_drafts-description" variant={PageSectionVariants.light}>
                    <TextContent>
                        Browse drafts (available to be edited) for this Registry instance.
                    </TextContent>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true} data-testid="drafts">
                    <p>TBD</p>
                </PageSection>
            </PageDataLoader>
        </PageErrorHandler>
    );

};
