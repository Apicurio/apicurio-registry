import { FunctionComponent } from "react";
import {
    Card,
    CardBody,
    CardTitle,
    Flex,
    FlexItem,
    Spinner
} from "@patternfly/react-core";
import "./StatsCard.css";

export type StatsCardProps = {
    title: string;
    value: number | undefined;
    icon: React.ReactNode;
    isLoading: boolean;
    onClick?: () => void;
};

export const StatsCard: FunctionComponent<StatsCardProps> = ({
    title,
    value,
    icon,
    isLoading,
    onClick
}: StatsCardProps) => {
    return (
        <Card
            className="stats-card"
            isClickable={!!onClick}
            onClick={onClick}
            data-testid={`stats-card-${title.toLowerCase().replace(/\s+/g, "-")}`}
        >
            <CardTitle>{title}</CardTitle>
            <CardBody>
                <Flex alignItems={{ default: "alignItemsCenter" }}>
                    <FlexItem className="stats-icon">{icon}</FlexItem>
                    <FlexItem className="stats-value">
                        {isLoading ? (
                            <Spinner size="md" />
                        ) : (
                            <span className="value-number">{value?.toLocaleString() ?? 0}</span>
                        )}
                    </FlexItem>
                </Flex>
            </CardBody>
        </Card>
    );
};
