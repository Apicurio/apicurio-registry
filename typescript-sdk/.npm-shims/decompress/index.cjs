"use strict";

// Same call signature as decompress@4: decompress(input, output?, options?) => Promise<File[]>
module.exports = (input, output, options) =>
    import("@xhmikosr/decompress").then((m) => m.default(input, output, options));
