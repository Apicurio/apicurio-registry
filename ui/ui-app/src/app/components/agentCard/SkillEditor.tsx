import { FunctionComponent, useState } from "react";
import "./SkillEditor.css";
import {
    Button,
    Form,
    FormGroup,
    FormHelperText,
    HelperText,
    HelperTextItem,
    TextArea,
    TextInput,
    Title,
    ActionGroup,
    Label,
    LabelGroup,
    InputGroup,
    InputGroupItem,
    DataList,
    DataListItem,
    DataListItemRow,
    DataListItemCells,
    DataListCell,
    DataListAction
} from "@patternfly/react-core";
import { PlusCircleIcon, TrashIcon } from "@patternfly/react-icons";
import { AgentSkill } from "./AgentCardSkills";
import { If } from "@apicurio/common-ui-components";

/**
 * Properties
 */
export type SkillEditorProps = {
    skills: AgentSkill[];
    onChange: (skills: AgentSkill[]) => void;
    showTitle?: boolean;
};

/**
 * Component for editing Agent Card skills.
 */
export const SkillEditor: FunctionComponent<SkillEditorProps> = (props: SkillEditorProps) => {
    const { skills, onChange, showTitle = true } = props;
    const [editingSkill, setEditingSkill] = useState<AgentSkill | null>(null);
    const [isAdding, setIsAdding] = useState(false);
    const [newTag, setNewTag] = useState("");
    const [newExample, setNewExample] = useState("");

    const emptySkill: AgentSkill = {
        id: "",
        name: "",
        description: "",
        tags: []
    };

    const isDuplicateSkillId: boolean =
        isAdding && !!editingSkill?.id?.trim() && skills.some(s => s.id.trim() === editingSkill?.id?.trim());

    const handleAddSkill = (): void => {
        setEditingSkill({ ...emptySkill });
        setIsAdding(true);
    };

    const handleEditSkill = (skill: AgentSkill): void => {
        setEditingSkill({ ...skill });
        setIsAdding(false);
    };

    const handleDeleteSkill = (skillId: string): void => {
        const updated = skills.filter(s => s.id !== skillId);
        onChange(updated);
    };

    const handleSaveSkill = (): void => {
        if (!editingSkill || !editingSkill.id || !editingSkill.name) {
            return;
        }

        if (isDuplicateSkillId) {
            return;
        }

        if (isAdding) {
            onChange([...skills, editingSkill]);
        } else {
            const updated = skills.map(s =>
                s.id === editingSkill.id ? editingSkill : s
            );
            onChange(updated);
        }
        setEditingSkill(null);
        setIsAdding(false);
    };

    const handleCancelEdit = (): void => {
        setEditingSkill(null);
        setIsAdding(false);
    };

    const handleAddTag = (): void => {
        if (!editingSkill || !newTag.trim()) return;
        const tags = [...(editingSkill.tags || []), newTag.trim()];
        setEditingSkill({ ...editingSkill, tags });
        setNewTag("");
    };

    const handleRemoveTag = (indexToRemove: number): void => {
        if (!editingSkill) return;
        const tags = (editingSkill.tags || []).filter((_, i) => i !== indexToRemove);
        setEditingSkill({ ...editingSkill, tags });
    };

    const handleAddExample = (): void => {
        if (!editingSkill || !newExample.trim()) return;
        const examples = [...(editingSkill.examples || []), newExample.trim()];
        setEditingSkill({ ...editingSkill, examples });
        setNewExample("");
    };

    const handleRemoveExample = (indexToRemove: number): void => {
        if (!editingSkill) return;
        const examples = (editingSkill.examples || []).filter((_, i) => i !== indexToRemove);
        setEditingSkill({ ...editingSkill, examples });
    };

    const renderSkillForm = (): React.ReactElement => {
        if (!editingSkill) return <></>;

        return (
            <Form className="skill-form">
                <FormGroup label="Skill ID" isRequired fieldId="skill-id">
                    <TextInput
                        id="skill-id"
                        value={editingSkill.id}
                        onChange={(_event, value) => setEditingSkill({ ...editingSkill, id: value })}
                        placeholder="e.g., schema-validation"
                        isDisabled={!isAdding}
                        validated={isDuplicateSkillId ? "error" : "default"}
                    />
                    {isDuplicateSkillId && (
                        <FormHelperText>
                            <HelperText>
                                <HelperTextItem variant="error">
                                    A skill with this ID already exists.
                                </HelperTextItem>
                            </HelperText>
                        </FormHelperText>
                    )}
                </FormGroup>
                <FormGroup label="Name" isRequired fieldId="skill-name">
                    <TextInput
                        id="skill-name"
                        value={editingSkill.name}
                        onChange={(_event, value) => setEditingSkill({ ...editingSkill, name: value })}
                        placeholder="e.g., Schema Validation"
                    />
                </FormGroup>
                <FormGroup label="Description" fieldId="skill-description">
                    <TextArea
                        id="skill-description"
                        value={editingSkill.description || ""}
                        onChange={(_event, value) => setEditingSkill({ ...editingSkill, description: value })}
                        placeholder="Describe what this skill does..."
                        rows={3}
                    />
                </FormGroup>
                <FormGroup label="Tags" fieldId="skill-tags">
                    <InputGroup>
                        <InputGroupItem isFill>
                            <TextInput
                                id="new-tag"
                                value={newTag}
                                onChange={(_event, value) => setNewTag(value)}
                                placeholder="Add a tag..."
                                onKeyDown={(e) => {
                                    if (e.key === "Enter") {
                                        e.preventDefault();
                                        handleAddTag();
                                    }
                                }}
                            />
                        </InputGroupItem>
                        <InputGroupItem>
                            <Button variant="control" onClick={handleAddTag}>
                                Add
                            </Button>
                        </InputGroupItem>
                    </InputGroup>
                    <If condition={(editingSkill.tags?.length ?? 0) > 0}>
                        <LabelGroup className="tags-list">
                            {editingSkill.tags?.map((tag, index) => (
                                <Label
                                    key={index}
                                    color="blue"
                                    onClose={() => handleRemoveTag(index)}
                                >
                                    {tag}
                                </Label>
                            ))}
                        </LabelGroup>
                    </If>
                </FormGroup>
                <FormGroup label="Examples" fieldId="skill-examples">
                    <InputGroup>
                        <InputGroupItem isFill>
                            <TextInput
                                id="new-example"
                                value={newExample}
                                onChange={(_event, value) => setNewExample(value)}
                                placeholder="Add an example (e.g. 'What is the weather?')"
                                onKeyDown={(e) => {
                                    if (e.key === "Enter") {
                                        e.preventDefault();
                                        handleAddExample();
                                    }
                                }}
                            />
                        </InputGroupItem>
                        <InputGroupItem>
                            <Button variant="control" onClick={handleAddExample}>
                                Add
                            </Button>
                        </InputGroupItem>
                    </InputGroup>
                    <If condition={(editingSkill.examples?.length ?? 0) > 0}>
                        <LabelGroup className="examples-list" style={{ marginTop: "8px" }}>
                            {editingSkill.examples?.map((example, index) => (
                                <Label
                                    key={index}
                                    color="green"
                                    onClose={() => handleRemoveExample(index)}
                                >
                                    {example}
                                </Label>
                            ))}
                        </LabelGroup>
                    </If>
                </FormGroup>
                <ActionGroup>
                    <Button variant="primary" onClick={handleSaveSkill} isDisabled={isDuplicateSkillId}>
                        {isAdding ? "Add Skill" : "Save Changes"}
                    </Button>
                    <Button variant="link" onClick={handleCancelEdit}>
                        Cancel
                    </Button>
                </ActionGroup>
            </Form>
        );
    };

    const renderSkillsList = (): React.ReactElement => {
        if (skills.length === 0) {
            return (
                <div className="no-skills">
                    <span className="empty-state-text">No skills defined. Add a skill to get started.</span>
                </div>
            );
        }

        return (
            <DataList aria-label="Skills list" isCompact className="skills-list">
                {skills.map((skill) => (
                    <DataListItem key={skill.id} aria-labelledby={`skill-item-${skill.id}`}>
                        <DataListItemRow>
                            <DataListItemCells
                                dataListCells={[
                                    <DataListCell key="name" width={2}>
                                        <div className="skill-name" id={`skill-item-${skill.id}`}>
                                            {skill.name}
                                        </div>
                                        <div className="skill-id">{skill.id}</div>
                                    </DataListCell>,
                                    <DataListCell key="description" width={3}>
                                        {skill.description || <span className="empty-state-text">No description</span>}
                                    </DataListCell>
                                ]}
                            />
                            <DataListAction
                                aria-label="Actions"
                                aria-labelledby={`skill-item-${skill.id}`}
                                id={`skill-actions-${skill.id}`}
                            >
                                <Button variant="link" onClick={() => handleEditSkill(skill)}>
                                    Edit
                                </Button>
                                <Button icon={<TrashIcon />}
                                    variant="plain"
                                    aria-label={`Delete skill ${skill.name}`}
                                    onClick={() => handleDeleteSkill(skill.id)}
                                    className="delete-btn"
                                />
                            </DataListAction>
                        </DataListItemRow>
                    </DataListItem>
                ))}
            </DataList>
        );
    };

    return (
        <div className="skill-editor">
            <If condition={showTitle}>
                <Title headingLevel="h4" className="section-title">Skills</Title>
            </If>

            {editingSkill ? (
                renderSkillForm()
            ) : (
                <>
                    {renderSkillsList()}
                    <Button
                        variant="link"
                        icon={<PlusCircleIcon />}
                        onClick={handleAddSkill}
                        className="add-skill-btn"
                    >
                        Add Skill
                    </Button>
                </>
            )}
        </div>
    );
};
