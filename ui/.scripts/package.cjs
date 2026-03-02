const shell = require("shelljs");

shell.rm("-rf", "dist");
shell.mkdir("-p", "dist/docs");
shell.mkdir("-p", "dist/docs-json");
shell.mkdir("-p", "dist/editors");

shell.cp("-r", "./ui-app/dist/*", "dist");
shell.cp("-r", "./ui-docs/dist/*", "dist/docs");
shell.cp("-r", "./ui-docs-json/dist/*", "dist/docs-json");
shell.cp("-r", "./ui-editors/dist/*", "dist/editors");

