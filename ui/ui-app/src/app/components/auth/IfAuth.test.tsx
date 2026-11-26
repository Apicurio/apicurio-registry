// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("@apitomy/common-ui-components", () => ({
    useAuth: () => ({ isAuthenticated: () => Promise.resolve(true) })
}));

vi.mock("@services/useUserService.ts", () => ({
    useUserService: () => ({
        isUserAdmin: () => false,
        isUserDeveloper: () => true,
        isUserId: () => false
    })
}));

import { IfAuth } from "./IfAuth";

describe("IfAuth with resource permissions", () => {
    afterEach(cleanup);

    it("hides a write action on a read-only resource", () => {
        render(<IfAuth isDeveloper={true} permissions={{ read: true, write: false, admin: false }}>
            <button>Delete group</button>
        </IfAuth>);
        expect(screen.queryByText("Delete group")).not.toBeInTheDocument();
    });

    it("shows a write action on a writable resource", () => {
        render(<IfAuth isDeveloper={true} permissions={{ read: true, write: true, admin: false }}>
            <button>Delete group</button>
        </IfAuth>);
        expect(screen.getByText("Delete group")).toBeInTheDocument();
    });

    it("shows the action as before when no permissions are reported", () => {
        render(<IfAuth isDeveloper={true}>
            <button>Delete group</button>
        </IfAuth>);
        expect(screen.getByText("Delete group")).toBeInTheDocument();
    });

    it("checks the required permission", () => {
        render(<IfAuth permissions={{ read: true, write: false, admin: false }} requires="read">
            <button>Download</button>
        </IfAuth>);
        expect(screen.getByText("Download")).toBeInTheDocument();
    });

    it("still applies the role checks when the resource is writable", () => {
        render(<IfAuth isAdmin={true} permissions={{ read: true, write: true, admin: true }}>
            <button>Admin only</button>
        </IfAuth>);
        expect(screen.queryByText("Admin only")).not.toBeInTheDocument();
    });
});
