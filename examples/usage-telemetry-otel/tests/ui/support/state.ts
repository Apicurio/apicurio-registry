import type { IdentifierType, ProcessScenario, RunState, SeriesRow } from "../lib/input.mjs";
import { selectRows } from "../lib/plan.mjs";
import type { Selection } from "../lib/plan.mjs";

// The input is validated and typed by lib/input.mjs, once, before the browser starts, and the selections the flows need are decided
// by lib/plan.mjs in the same step; this module only derives what the tests expect from the typed projection.
export type { IdentifierType, ProcessScenario, RunState, Selection, SeriesRow };
export { selectRows };

/** One expected table row as the dashboard shows it: "client TAB type TAB identifier TAB operation TAB count". */
export const rowText = (row: SeriesRow): string =>
    [row.clientId, row.identifierType, row.identifierValue, row.operation, String(row.count)].join("\t");

export const sortedRows = (rows: SeriesRow[]): string[] => rows.map(rowText).sort();

export const totalOf = (rows: SeriesRow[]): number => rows.reduce((sum, row) => sum + row.count, 0);
