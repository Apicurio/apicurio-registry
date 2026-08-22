// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PromptTemplatePreviewPanel } from "./PromptTemplatePreviewPanel";

afterEach(() => {
    cleanup();
});

describe("PromptTemplatePreviewPanel", () => {
    it("renders an input for each detected variable", () => {
        render(<PromptTemplatePreviewPanel template="Hello {{name}}, welcome to {place}!" />);

        expect(screen.getByLabelText("name")).toBeInTheDocument();
        expect(screen.getByLabelText("place")).toBeInTheDocument();
    });

    it("updates the preview in real-time as the user types", async () => {
        const user = userEvent.setup();
        render(<PromptTemplatePreviewPanel template="Hello {{name}}!" />);

        await user.type(screen.getByLabelText("name"), "Alice");

        expect(screen.getByText("Hello Alice!")).toBeInTheDocument();
    });

    it("leaves unfilled variables as raw placeholders in the preview", async () => {
        const user = userEvent.setup();
        render(<PromptTemplatePreviewPanel template="{{greeting}}, {name}!" />);

        await user.type(screen.getByLabelText("greeting"), "Hi");

        expect(screen.getByText("Hi, {name}!")).toBeInTheDocument();
    });
});
