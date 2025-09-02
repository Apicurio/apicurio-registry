package io.apicurio.registry.cli;

import io.apicurio.registry.cli.services.Update;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.eclipse.microprofile.config.ConfigProvider;
import org.semver4j.Semver;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.RunLast;

import java.net.URI;
import java.util.HashMap;

import static java.lang.System.exit;
import static picocli.CommandLine.Model.UsageMessageSpec.SECTION_KEY_HEADER_HEADING;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.ScopeType.INHERIT;

@Command(
        name = "acr",
        description = "Apicurio Registry CLI",
        subcommands = {
                ContextCommand.class,
                GroupCommand.class,
                InstallCommand.class,
                UpdateCommand.class,
                VersionCommand.class
        },
        exitCodeListHeading = "Exit Codes:%n",
        exitCodeList = {
                "0: Successful execution.",
                "1: Application error.",
                "2: Input validation error.",
                "3: Apicurio Registry server error."
        }
)
public final class Acr {

    private static final Logger log = LogManager.getRootLogger();

    @Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose output.",
            scope = INHERIT
    )
    @Getter
    private boolean verbose;

    @Option(
            names = {"-h", "--help"},
            description = "Print a help message and exit.",
            usageHelp = true,
            scope = INHERIT
    )
    private boolean _ignored;

    private Acr() {
    }

    private int init(ParseResult parseResult) {
        if (verbose) {
            Configurator.reconfigure(URI.create("log4j2-verbose.xml"));
            // I don't know why this only works for the root logger... TODO: Investigate
            log.debug("Verbose logging enabled.");
        }
        if (ConfigProvider.getConfig().getOptionalValue("acr.home", String.class).isPresent()
                && ConfigProvider.getConfig().getValue("acr.update.check.enabled", Boolean.class)) {
            var latest = Semver.coerce(Update.getInstance().getLatestVersion());
            if (latest == null) {
                log.warn("Could not determine the latest version of the CLI.");
            } else {
                var current = Semver.parse(ConfigProvider.getConfig().getValue("version", String.class));
                if (current.isLowerThan(latest)) {
                    System.err.println("A newer version of the Apicurio Registry CLI is available. " +
                            "Run `acr update` to update to version '{}' or `export ACR_UPDATE_CHECK_ENABLED=false` to disable this message.");
                }
            }
        }
        return new RunLast().execute(parseResult); // Delegate to the default execution strategy...
    }

    public static void main(String[] args) {
        var cmd = createCLI();
        exit(cmd.execute(args));
    }

    static CommandLine createCLI() {
        var acr = new Acr();
        var cmd = new CommandLine(acr);
        cmd.setExecutionStrategy(acr::init);
        var helpSections = new HashMap<>(cmd.getHelpSectionMap());
        helpSections.put(SECTION_KEY_HEADER_HEADING, help -> ART_SMALL + "\n" + help.headerHeading());
        cmd.setHelpSectionMap(helpSections);
        return cmd;
    }

    // Generated with: https://patorjk.com/software/taag/#p=display&f=Small+Slant&t=Apicurio&x=none&w=80
    private static final String ART_SMALL = """
               ___        _              _
              / _ | ___  (_)_____ ______(_)__
             / __ |/ _ \\/ / __/ // / __/ / _ \\
            /_/ |_/ .__/_/\\__/\\_,_/_/ /_/\\___/
                 /_/""";
}
