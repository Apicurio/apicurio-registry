import { Skeleton } from "@patternfly/react-core";
import { Td, Tr } from "@patternfly/react-table";

type Props = {
  columns: number;
  rows: number;
  getTd?: (index: number) => typeof Td;
};
export function TableSkeleton({ columns, rows, getTd = () => Td }: Props) {
    const skeletonCells = new Array(columns).fill(0).map((_, index) => {
        const Td = getTd(index);
        return (
            <Td key={`cell_${index}`}>
                <Skeleton
                    screenreaderText={
                        index === 0
                            ? "Loading data"
                            : undefined
                    }
                />
            </Td>
        );
    });
    const skeletonRows = new Array(rows)
        .fill(0)
        .map((_, index) => <Tr key={`row_${index}`}>{skeletonCells}</Tr>);
    return <>{skeletonRows}</>;
}
