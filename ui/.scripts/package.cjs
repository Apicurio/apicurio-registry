const shell = require("shelljs");

shell.rm("-rf", "dist");
shell.mkdir("-p", "dist/docs");

shell.cp("-r", "./ui-app/dist/*", "dist");
shell.cp("-r", "./ui-docs/dist/*", "dist/docs");

