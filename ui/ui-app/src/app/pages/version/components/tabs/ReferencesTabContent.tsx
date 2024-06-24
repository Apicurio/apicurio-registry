import { FunctionComponent, useEffect, useState } from "react";
import "./ReferencesTabContent.css";
import { EmptyState, EmptyStateBody, EmptyStateVariant, Title } from "@patternfly/react-core";
import { ReferenceList, ReferencesSort } from "./ReferenceList.tsx";
import {
    ReferencesToolbar,
    ReferencesToolbarFilterCriteria
} from "@app/pages/version/components/tabs/ReferencesToolbar.tsx";
import { ListWithToolbar } from "@apicurio/common-ui-components";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { Paging } from "@models/paging.model.ts";
import {
    ArtifactReference,
    ReferenceType,
    ReferenceTypeObject,
    VersionMetaData
} from "@sdk/lib/generated-client/models";

/**
 * Properties
 */
export type ReferencesTabContentProps = {
    version: VersionMetaData | null;
};

/**
 * The UI of the "References" tab in the artifact version details page.
 */
export const ReferencesTabContent: FunctionComponent<ReferencesTabContentProps> = ({ version }: ReferencesTabContentProps) => {
    const [ isLoading, setLoading ] = useState<boolean>(true);
    const [ isError, setError ] = useState<boolean>(false);
    const [ allReferences, setAllReferences ] = useState<ArtifactReference[]>([]);
    const [ references, setReferences ] = useState<ArtifactReference[]>([]);
    const [ referenceCount, setReferenceCount ] = useState<number>(0);
    const [ criteria, setCriteria ] = useState<ReferencesToolbarFilterCriteria>({
        filterSelection: "name",
        filterValue: ""
    });
    const [ paging, setPaging ] = useState<Paging>({
        page: 1,
        pageSize: 20
    });
    const [ sort, setSort ] = useState<ReferencesSort>({
        direction: "asc",
        by: "name"
    });
    const [ referenceType, setReferenceType ] = useState<ReferenceType>(ReferenceTypeObject.OUTBOUND);

    const groups: GroupsService = useGroupsService();
    const logger: LoggerService = useLoggerService();

    // Whenever the artifact or the type of references to display changes, query for all its references.
    useEffect(() => {
        setLoading(true);

        groups.getArtifactReferences(version?.globalId as number, referenceType).then(references => {
            setAllReferences(references);
        }).catch(error => {
            logger.error(error);
            setLoading(false);
            setError(true);
        });
    }, [version, referenceType]);

    // Whenever we get new references, or the paging or sorting changes, perform filtering/paging on the references
    useEffect(() => {
        let refs: ArtifactReference[] = allReferences.filter((ref) => {
            if (criteria.filterSelection === "name") {
                if (criteria.filterValue) {
                    return ref.name!.toLowerCase().includes(criteria.filterValue.toLowerCase());
                }
            }
            return true;
        });
        setReferenceCount(refs.length);
        refs.sort((ref1, ref2) => {
            if (sort.by === "name") {
                return ref1.name!.localeCompare(ref2.name!);
            }
            if (sort.by === "id") {
                return ref1.artifactId!.localeCompare(ref2.artifactId!);
            }
            if (sort.by === "group") {
                const g1: string = ref1.groupId || "";
                const g2: string = ref2.groupId || "";
                return g1.localeCompare(g2);
            }
            return 0;
        });
        if (sort.direction === "desc") {
            refs.reverse();
        }
        const startIndex: number = (paging.page - 1) * paging.pageSize;
        const endIndex: number = startIndex + paging.pageSize;
        refs = refs.slice(startIndex, endIndex);
        setReferences(refs);
        setLoading(false);
    }, [allReferences, paging, sort, criteria]);

    const onSetPage = (_event: any, newPage: number, perPage?: number): void => {
        const newPaging: Paging = {
            page: newPage,
            pageSize: perPage ? perPage : paging.pageSize
        };
        setPaging(newPaging);
    };

    const onPerPageSelect = (_event: any, newPerPage: number): void => {
        const newPaging: Paging = {
            page: paging.page,
            pageSize: newPerPage
        };
        setPaging(newPaging);
    };

    const onToggleReferenceType = (): void => {
        if (referenceType === ReferenceTypeObject.INBOUND) {
            setReferenceType(ReferenceTypeObject.OUTBOUND);
        } else {
            setReferenceType(ReferenceTypeObject.INBOUND);
        }
    };

    const toolbar = (<ReferencesToolbar
        referenceType={ referenceType }
        references={ references }
        totalReferenceCount={ referenceCount }
        onCriteriaChange={ setCriteria }
        criteria={ criteria }
        paging={ paging }
        onPerPageSelect={ onPerPageSelect }
        onSetPage={ onSetPage }
        onToggleReferenceType={ onToggleReferenceType } />);

    const emptyState = (<EmptyState variant={EmptyStateVariant.xs}>
        <Title headingLevel="h4" size="md">None found</Title>
        <EmptyStateBody>No references found.</EmptyStateBody>
    </EmptyState>);

    return (
        <div className="references-tab-content">
            <div className="refs-toolbar-and-table">
                <ListWithToolbar toolbar={ toolbar }
                    emptyState={ emptyState }
                    filteredEmptyState={ emptyState }
                    isLoading={ isLoading }
                    isError={ isError }
                    isFiltered={  true }
                    isEmpty={ references.length === 0 }
                >
                    <ReferenceList references={ references } sort={ sort } onSort={ setSort } />
                </ListWithToolbar>
            </div>
        </div>
    );
};
