import { FunctionComponent } from "react";
import {
    Card,
    CardBody,
    CardTitle,
    Flex,
    FlexItem,
    Label,
    Spinner
} from "@patternfly/react-core";
import { UsageSummary } from "@sdk/lib/generated-client/models";

export type UsageSummaryCardProps = {
    data: UsageSummary | null;
    isLoading: boolean;
};

export const UsageSummaryCard: FunctionComponent<UsageSummaryCardProps> = ({
    data,
    isLoading
}: UsageSummaryCardProps) => {
    if (!data && !isLoading) {
        return null;
    }

    return (
        <Card data-testid="usage-summary-card">
            <CardTitle>Schema Usage</CardTitle>
            <CardBody>
                {isLoading ? (
                    <Spinner size="md" />
                ) : (
                    <Flex spaceItems={{ default: "spaceItemsMd" }}>
                        <FlexItem>
                            <Label color="green">Active: {data?.active ?? 0}</Label>
                        </FlexItem>
                        <FlexItem>
                            <Label color="orange">Stale: {data?.stale ?? 0}</Label>
                        </FlexItem>
                        <FlexItem>
                            <Label color="red">Dead: {data?.dead ?? 0}</Label>
                        </FlexItem>
                    </Flex>
                )}
            </CardBody>
        </Card>
    );
};
