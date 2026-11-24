import { readFileSync } from "node:fs";
import { inflateRawSync } from "node:zlib";

export interface ZipEntry {
    name: string;
    data: Buffer;
}

/** Reads every entry of a ZIP file (stored or deflated, as Playwright writes its traces); no dependency, no extraction to disk. */
export function readZip(path: string): ZipEntry[] {
    const bytes = readFileSync(path);
    let end = bytes.length - 22;
    while (end >= 0 && bytes.readUInt32LE(end) !== 0x06054b50) {
        end -= 1;
    }
    if (end < 0) {
        throw new Error(`${path} is not a ZIP archive`);
    }
    const count = bytes.readUInt16LE(end + 10);
    let offset = bytes.readUInt32LE(end + 16);
    const entries: ZipEntry[] = [];
    for (let index = 0; index < count; index += 1) {
        const method = bytes.readUInt16LE(offset + 10);
        const compressed = bytes.readUInt32LE(offset + 20);
        const nameLength = bytes.readUInt16LE(offset + 28);
        const extraLength = bytes.readUInt16LE(offset + 30);
        const commentLength = bytes.readUInt16LE(offset + 32);
        const header = bytes.readUInt32LE(offset + 42);
        const name = bytes.toString("utf8", offset + 46, offset + 46 + nameLength);
        const start = header + 30 + bytes.readUInt16LE(header + 26) + bytes.readUInt16LE(header + 28);
        const raw = bytes.subarray(start, start + compressed);
        entries.push({ name, data: method === 8 ? inflateRawSync(raw) : Buffer.from(raw) });
        offset += 46 + nameLength + extraLength + commentLength;
    }
    return entries;
}

export interface TraceFacts {
    /** Every byte of every entry as one latin1 string, for marker searches (images and text alike). */
    text: string;
    names: string[];
    /** Non-empty lines of the *.network entries: recorded requests and responses. */
    networkRecords: number;
    /** Entries below resources/: stored response bodies, DOM snapshots' resources. */
    resources: number;
    /** The "type" of every event line of the *.trace entries. */
    eventTypes: Set<string>;
    /** The recorded actions as "Class.method", such as "Page.reload". */
    actions: Set<string>;
    images: number;
}

/** What a Playwright trace archive actually contains, for checks that must look at the archive and not at the options used. */
export function inspectTrace(path: string): TraceFacts {
    const entries = readZip(path);
    const eventTypes = new Set<string>();
    const actions = new Set<string>();
    let networkRecords = 0;
    for (const entry of entries) {
        const lines = entry.data.toString("utf8").split("\n").filter((line) => line !== "");
        if (entry.name.endsWith(".network")) {
            networkRecords += lines.length;
        } else if (entry.name.endsWith(".trace")) {
            for (const line of lines) {
                const event = JSON.parse(line) as { type: string; class?: string; method?: string };
                eventTypes.add(event.type);
                if (event.type === "before" && event.class !== undefined && event.method !== undefined) {
                    actions.add(`${event.class}.${event.method}`);
                }
            }
        }
    }
    return {
        text: entries.map((entry) => entry.data.toString("latin1")).join("\n"),
        names: entries.map((entry) => entry.name),
        networkRecords,
        resources: entries.filter((entry) => entry.name.startsWith("resources/")).length,
        eventTypes,
        actions,
        images: entries.filter((entry) => /\.(jpe?g|png)$/.test(entry.name)).length,
    };
}
