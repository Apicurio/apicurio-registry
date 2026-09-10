import {
  Label,
} from "@patternfly/react-core";
import {
  K8sResourceCommon,
  ListPageBody,
  ListPageCreate,
  ListPageFilter,
  ListPageHeader,
  RowFilter,
  TableColumn,
  TableData,
  Timestamp,
  useActiveColumns,
  useK8sWatchResource,
  useListPageFilter,
  VirtualizedTable,
} from "@openshift-console/dynamic-plugin-sdk";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import {
  ApicurioRegistry3,
  ApicurioRegistry3Model,
  getAppUrl,
  getReadyCondition,
  getStorageType,
  getUiUrl,
  isAuthEnabled,
} from "../utils/k8s";
import RegistryStatusBadge from "./RegistryStatusBadge";

const columns: TableColumn<ApicurioRegistry3>[] = [
  { title: "Name", id: "name" },
  { title: "Namespace", id: "namespace" },
  { title: "Status", id: "status" },
  { title: "Storage", id: "storage-type" },
  { title: "Auth", id: "auth" },
  { title: "Replicas", id: "replicas" },
  { title: "API", id: "api-url" },
  { title: "UI", id: "ui-url" },
  { title: "Age", id: "age" },
];

const ExternalLinkCell: React.FC<{ url: string | null }> = ({ url }) => {
  if (!url) return <span>-</span>;
  const host = new URL(url).host;
  return (
    <a href={url} target="_blank" rel="noopener noreferrer">
      {host} ↗
    </a>
  );
};

const RegistryRow: React.FC<{
  obj: ApicurioRegistry3;
  activeColumnIDs: Set<string>;
}> = ({ obj, activeColumnIDs }) => {
  const readyCondition = getReadyCondition(obj);
  const resourcePath = `/k8s/ns/${obj.metadata.namespace}/${ApicurioRegistry3Model.apiGroup}~${ApicurioRegistry3Model.apiVersion}~${ApicurioRegistry3Model.kind}/${obj.metadata.name}`;

  return (
    <>
      <TableData id="name" activeColumnIDs={activeColumnIDs}>
        <Link to={resourcePath}>{obj.metadata.name}</Link>
      </TableData>
      <TableData id="namespace" activeColumnIDs={activeColumnIDs}>
        <Link to={`/k8s/cluster/namespaces/${obj.metadata.namespace}`}>
          {obj.metadata.namespace}
        </Link>
      </TableData>
      <TableData id="status" activeColumnIDs={activeColumnIDs}>
        <RegistryStatusBadge condition={readyCondition} />
      </TableData>
      <TableData id="storage-type" activeColumnIDs={activeColumnIDs}>
        {getStorageType(obj)}
      </TableData>
      <TableData id="auth" activeColumnIDs={activeColumnIDs}>
        {isAuthEnabled(obj) ? (
          <Label color="green" isCompact>On</Label>
        ) : (
          <Label color="grey" isCompact>Off</Label>
        )}
      </TableData>
      <TableData id="replicas" activeColumnIDs={activeColumnIDs}>
        {obj.spec?.app?.replicas ?? 1}
      </TableData>
      <TableData id="api-url" activeColumnIDs={activeColumnIDs}>
        <ExternalLinkCell url={getAppUrl(obj)} />
      </TableData>
      <TableData id="ui-url" activeColumnIDs={activeColumnIDs}>
        <ExternalLinkCell url={getUiUrl(obj)} />
      </TableData>
      <TableData id="age" activeColumnIDs={activeColumnIDs}>
        <Timestamp timestamp={obj.metadata.creationTimestamp} />
      </TableData>
    </>
  );
};

const RegistryListPage: React.FC<{ namespace: string }> = ({ namespace }) => {
  const { t } = useTranslation("plugin__apicurio-registry");

  const [registries, loaded, loadError] = useK8sWatchResource<
    ApicurioRegistry3[]
  >({
    groupVersionKind: {
      group: ApicurioRegistry3Model.apiGroup,
      version: ApicurioRegistry3Model.apiVersion,
      kind: ApicurioRegistry3Model.kind,
    },
    isList: true,
    namespace,
  });

  const filters: RowFilter<ApicurioRegistry3>[] = [
    {
      filterGroupName: t("Status"),
      type: "status",
      items: [
        { id: "Ready", title: t("Ready") },
        { id: "Not Ready", title: t("Not Ready") },
      ],
      filter: (input, registry) => {
        if (!input.selected?.length) return true;
        const readyCondition = getReadyCondition(registry);
        const isReady = readyCondition?.status === "True";
        if (input.selected.includes("Ready") && isReady) return true;
        if (input.selected.includes("Not Ready") && !isReady) return true;
        return false;
      },
      reducer: (registry) => {
        const readyCondition = getReadyCondition(registry);
        return readyCondition?.status === "True" ? "Ready" : "Not Ready";
      },
    },
  ];

  const [data, filteredData, onFilterChange] = useListPageFilter(
    registries as K8sResourceCommon[],
    filters
  );

  const [activeColumns] = useActiveColumns({ columns });

  return (
    <>
      <ListPageHeader title={t("Apicurio Registry Instances")}>
        <ListPageCreate
          groupVersionKind={{
            group: ApicurioRegistry3Model.apiGroup,
            version: ApicurioRegistry3Model.apiVersion,
            kind: ApicurioRegistry3Model.kind,
          }}
        >
          {t("Create ApicurioRegistry")}
        </ListPageCreate>
      </ListPageHeader>
      <ListPageBody>
        <ListPageFilter
          data={data}
          loaded={loaded}
          rowFilters={filters}
          onFilterChange={onFilterChange}
        />
        <VirtualizedTable<ApicurioRegistry3>
          data={filteredData as ApicurioRegistry3[]}
          unfilteredData={registries}
          loaded={loaded}
          loadError={loadError}
          columns={activeColumns}
          Row={RegistryRow}
        />
      </ListPageBody>
    </>
  );
};

export default RegistryListPage;
