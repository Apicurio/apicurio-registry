// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { CreateArtifact } from "@sdk/lib/generated-client/models";
import { CreateArtifactModal } from "./CreateArtifactModal";

vi.mock("@services/useUrlService.ts", () => ({
    useUrlService: () => ({ fetchUrlContent: vi.fn() })
}));
vi.mock("@services/useArtifactTypesService.ts", () => ({
    useArtifactTypesService: () => ({
        allTypesWithLabels: () => Promise.resolve([{ id: "JSON", label: "JSON Schema" }])
    })
}));
vi.mock("@services/useGroupsService.ts", () => ({
    useGroupsService: () => ({ detectContentReferences: vi.fn() })
}));
// labels.utils imports rest.utils, which loads the full SDK client; the modal only needs listToLabels.
vi.mock("@utils/rest.utils.ts", () => ({
    labelsToAny: (labels: unknown) => labels
}));
// The barrel pulls in editor components; only the two real form groups are needed here.
vi.mock("@app/components", async () => ({
    ...(await vi.importActual<object>("./LabelsFormGroup")),
    ...(await vi.importActual<object>("./ReferencesFormGroup"))
}));

const CONTENT: string = "{\"type\":\"object\"}";
const EDITED_CONTENT: string = "{\"type\":\"object\",\"title\":\"x\"}";

const onCreate = vi.fn();

const modal = (isOpen: boolean) => (
    <CreateArtifactModal isOpen={isOpen} onClose={() => {}} onCreate={onCreate} />
);

/** Lets pending promise callbacks run and React apply the resulting state updates. */
const flush = async (): Promise<void> => {
    await act(async () => {
        await new Promise((r) => setTimeout(r, 0));
    });
};

const clickButton = (name: string): void => {
    fireEvent.click(screen.getByRole("button", { name }));
};

const setContent = (value: string): void => {
    const textArea: HTMLElement = document.querySelector("textarea#artifact-content") as HTMLElement;
    fireEvent.change(textArea, { target: { value } });
};

const typeInto = (testId: string, value: string): void => {
    fireEvent.change(screen.getByTestId(testId), { target: { value } });
};

const addReference = (): void => {
    clickButton("Add reference");
    typeInto("references-form-name-0", "ref.json");
    typeInto("references-form-group-id-0", "g");
    typeInto("references-form-artifact-id-0", "a");
    typeInto("references-form-version-0", "1");
};

// PatternFly puts the test id on the <li>; the click handler is on the button inside it.
// The popper menu is not laid out in jsdom, so the option counts as hidden.
const selectJsonType = (): void => {
    fireEvent.click(screen.getByTestId("create-artifact-modal-type-select"));
    fireEvent.click(within(screen.getByTestId("create-artifact-modal-JSON")).getByRole("option", { hidden: true }));
};

const submitted = (): CreateArtifact => {
    expect(onCreate).toHaveBeenCalledTimes(1);
    return onCreate.mock.calls[0][1] as CreateArtifact;
};

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("CreateArtifactModal", () => {
    it("submits references added before the content was edited", async () => {
        render(modal(true));
        await flush();
        typeInto("create-artifact-modal-id", "my-artifact");
        clickButton("Next");
        clickButton("Next");
        setContent(CONTENT);
        clickButton("Next");
        addReference();

        clickButton("Back");
        setContent(EDITED_CONTENT);
        clickButton("Next");
        clickButton("Next");
        clickButton("Create");

        expect(submitted().firstVersion?.content).toEqual({
            content: EDITED_CONTENT,
            contentType: "application/json",
            references: [{ name: "ref.json", groupId: "g", artifactId: "a", version: "1" }]
        });
    });

    it("submits references when the content is not edited afterwards", async () => {
        render(modal(true));
        await flush();
        clickButton("Next");
        clickButton("Next");
        setContent(CONTENT);
        clickButton("Next");
        addReference();
        clickButton("Next");
        clickButton("Create");

        expect(submitted().firstVersion?.content?.references).toEqual([
            { name: "ref.json", groupId: "g", artifactId: "a", version: "1" }
        ]);
    });

    it("clears labels and type abandoned by Cancel when it is opened again", async () => {
        const { rerender } = render(modal(true));
        await flush();
        selectJsonType();
        expect(screen.getByTestId("create-artifact-modal-type-select")).toHaveTextContent("JSON Schema");
        clickButton("Next");
        clickButton("Add label");
        typeInto("edit-metadata-modal-label-name-0", "team");
        typeInto("edit-metadata-modal-label-value-0", "payments");

        rerender(modal(false));
        rerender(modal(true));
        await flush();

        expect(screen.getByTestId("create-artifact-modal-type-select")).toHaveTextContent("Auto-Detect");
        clickButton("Next");
        expect(screen.queryByTestId("edit-metadata-modal-label-name-0")).not.toBeInTheDocument();
    });

    it("submits the artifact labels and type the form shows", async () => {
        render(modal(true));
        await flush();
        selectJsonType();
        clickButton("Next");
        clickButton("Add label");
        typeInto("edit-metadata-modal-label-name-0", "team");
        typeInto("edit-metadata-modal-label-value-0", "payments");
        clickButton("Next");
        clickButton("Create");

        const data: CreateArtifact = submitted();
        expect(data.artifactType).toBe("JSON");
        expect(data.labels?.additionalData).toEqual({ team: "payments" });
    });
});
