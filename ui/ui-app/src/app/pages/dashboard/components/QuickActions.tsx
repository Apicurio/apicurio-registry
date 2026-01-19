import { FunctionComponent } from "react";
import {
    Card,
    CardBody,
    CardTitle,
    Button,
    Flex,
    FlexItem
} from "@patternfly/react-core";
import {
    PlusCircleIcon,
    SearchIcon,
    CubesIcon,
    CogIcon
} from "@patternfly/react-icons";
import "./QuickActions.css";

export type QuickActionsProps = {
    onCreateArtifact: () => void;
    onSearch: () => void;
    onExplore: () => void;
    onSettings: () => void;
};

export const QuickActions: FunctionComponent<QuickActionsProps> = ({
    onCreateArtifact,
    onSearch,
    onExplore,
    onSettings
}: QuickActionsProps) => {
    return (
        <Card className="quick-actions-card">
            <CardTitle>Quick Actions</CardTitle>
            <CardBody>
                <Flex direction={{ default: "column" }} spaceItems={{ default: "spaceItemsMd" }}>
                    <FlexItem>
                        <Button
                            variant="primary"
                            icon={<PlusCircleIcon />}
                            onClick={onCreateArtifact}
                            isBlock
                            data-testid="quick-action-create"
                        >
                            Create Artifact
                        </Button>
                    </FlexItem>
                    <FlexItem>
                        <Button
                            variant="secondary"
                            icon={<SearchIcon />}
                            onClick={onSearch}
                            isBlock
                            data-testid="quick-action-search"
                        >
                            Search Registry
                        </Button>
                    </FlexItem>
                    <FlexItem>
                        <Button
                            variant="secondary"
                            icon={<CubesIcon />}
                            onClick={onExplore}
                            isBlock
                            data-testid="quick-action-explore"
                        >
                            Explore Groups
                        </Button>
                    </FlexItem>
                    <FlexItem>
                        <Button
                            variant="tertiary"
                            icon={<CogIcon />}
                            onClick={onSettings}
                            isBlock
                            data-testid="quick-action-settings"
                        >
                            Settings
                        </Button>
                    </FlexItem>
                </Flex>
            </CardBody>
        </Card>
    );
};
