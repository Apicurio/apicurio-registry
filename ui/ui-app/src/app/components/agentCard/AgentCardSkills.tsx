import { FunctionComponent } from "react";
import "./AgentCardSkills.css";
import {
    DataList,
    DataListCell,
    DataListItem,
    DataListItemCells,
    DataListItemRow,
    Label,
    LabelGroup,
    Title,
    Tooltip
} from "@patternfly/react-core";

/**
 * Agent skill structure
 */
export interface AgentSkill {
    id: string;
    name: string;
    description?: string;
    tags?: string[];
    examples?: string[];
}

/**
 * Properties
 */
export type AgentCardSkillsProps = {
    skills?: AgentSkill[];
    showTitle?: boolean;
    compact?: boolean;
};

/**
 * Component to display Agent Card skills as a list.
 */
export const AgentCardSkills: FunctionComponent<AgentCardSkillsProps> = (props: AgentCardSkillsProps) => {
    const skills = props.skills || [];
    const showTitle = props.showTitle !== false;
    const compact = props.compact === true;

    const hasSkills = (): boolean => {
        return skills.length > 0;
    };

    const renderSkillTags = (skill: AgentSkill): React.ReactElement | null => {
        if (!skill.tags || skill.tags.length === 0) {
            return null;
        }
        return (
            <LabelGroup className="skill-tags">
                {skill.tags.map((tag, index) => (
                    <Label key={index} color="blue" isCompact>
                        {tag}
                    </Label>
                ))}
            </LabelGroup>
        );
    };

    const renderCompactSkills = (): React.ReactElement => {
        return (
            <LabelGroup className="skills-compact-list">
                {skills.map((skill) => (
                    <Tooltip key={skill.id} content={skill.description || skill.name}>
                        <Label color="purple" isCompact>
                            {skill.name}
                        </Label>
                    </Tooltip>
                ))}
            </LabelGroup>
        );
    };

    const renderDetailedSkills = (): React.ReactElement => {
        return (
            <DataList aria-label="Agent skills" isCompact className="skills-data-list">
                {skills.map((skill) => (
                    <DataListItem key={skill.id} aria-labelledby={`skill-${skill.id}`}>
                        <DataListItemRow>
                            <DataListItemCells
                                dataListCells={[
                                    <DataListCell key="name" width={2}>
                                        <div className="skill-name" id={`skill-${skill.id}`}>
                                            {skill.name}
                                        </div>
                                        <div className="skill-id">
                                            {skill.id}
                                        </div>
                                    </DataListCell>,
                                    <DataListCell key="description" width={4}>
                                        <div className="skill-description">
                                            {skill.description || <span className="empty-state-text">No description</span>}
                                        </div>
                                        {renderSkillTags(skill)}
                                    </DataListCell>
                                ]}
                            />
                        </DataListItemRow>
                    </DataListItem>
                ))}
            </DataList>
        );
    };

    if (!hasSkills()) {
        return (
            <div className="agent-card-skills">
                {showTitle && <Title headingLevel="h4" className="section-title">Skills</Title>}
                <span className="empty-state-text">No skills defined</span>
            </div>
        );
    }

    return (
        <div className="agent-card-skills">
            {showTitle && <Title headingLevel="h4" className="section-title">Skills ({skills.length})</Title>}
            {compact ? renderCompactSkills() : renderDetailedSkills()}
        </div>
    );
};
