#!/bin/bash
#
# Setup MCP Server for Claude Code Integration
#
# This script configures Claude Code to use the Apicurio Registry MCP server.
# It can either:
# 1. Use a Docker container (recommended for demo)
# 2. Build the MCP server JAR from source
# 3. Download a pre-built JAR
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_header() {
    echo ""
    echo -e "${BLUE}══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}══════════════════════════════════════════════════════════════${NC}"
}

print_step() {
    echo -e "${GREEN}▶ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}  $1${NC}"
}

print_header "MCP Server Setup for Claude Code"

echo ""
echo "This script will configure Claude Code to use the Apicurio Registry MCP server."
echo ""
echo "Options:"
echo "  1. Docker mode (recommended) - Use MCP server via Docker"
echo "  2. Build from source - Build the MCP server JAR"
echo "  3. Manual setup - Just show the configuration"
echo ""
read -p "Select option [1-3]: " OPTION

case $OPTION in
    1)
        print_header "Docker Mode Setup"

        # Create a wrapper script that runs the MCP server via Docker
        MCP_WRAPPER="$SCRIPT_DIR/mcp-server-wrapper.sh"

        cat > "$MCP_WRAPPER" << 'WRAPPER_EOF'
#!/bin/bash
# MCP Server Wrapper - Runs the MCP server via Docker
# This script is called by Claude Code

exec docker exec -i apicurio-mcp-server java -jar /app/apicurio-registry-mcp-server.jar
WRAPPER_EOF

        chmod +x "$MCP_WRAPPER"

        print_step "Created MCP wrapper script: $MCP_WRAPPER"

        # Generate Claude Code configuration
        CLAUDE_CONFIG=$(cat << EOF
{
  "mcpServers": {
    "apicurio-registry": {
      "command": "$MCP_WRAPPER",
      "args": [],
      "env": {
        "REGISTRY_URL": "http://localhost:8080"
      }
    }
  }
}
EOF
)
        ;;

    2)
        print_header "Build from Source"

        print_step "Building MCP server JAR..."

        cd "$PROJECT_ROOT"

        if [ ! -f "pom.xml" ]; then
            echo -e "${RED}Error: Not in Apicurio Registry source directory${NC}"
            exit 1
        fi

        # Build just the MCP module
        mvn clean package -pl mcp -am -DskipTests -q

        MCP_JAR="$PROJECT_ROOT/mcp/target/apicurio-registry-mcp-server-*-runner.jar"
        MCP_JAR_RESOLVED=$(ls $MCP_JAR 2>/dev/null | head -1)

        if [ -z "$MCP_JAR_RESOLVED" ]; then
            echo -e "${RED}Error: MCP JAR not found after build${NC}"
            exit 1
        fi

        print_step "MCP JAR built: $MCP_JAR_RESOLVED"

        # Generate Claude Code configuration
        CLAUDE_CONFIG=$(cat << EOF
{
  "mcpServers": {
    "apicurio-registry": {
      "command": "java",
      "args": ["-jar", "$MCP_JAR_RESOLVED"],
      "env": {
        "REGISTRY_URL": "http://localhost:8080",
        "APICURIO_MCP_SAFE_MODE": "false",
        "APICURIO_MCP_PAGING_LIMIT": "100"
      }
    }
  }
}
EOF
)
        ;;

    3)
        print_header "Manual Setup"

        CLAUDE_CONFIG=$(cat << 'EOF'
{
  "mcpServers": {
    "apicurio-registry": {
      "command": "java",
      "args": ["-jar", "/path/to/apicurio-registry-mcp-server.jar"],
      "env": {
        "REGISTRY_URL": "http://localhost:8080",
        "APICURIO_MCP_SAFE_MODE": "false",
        "APICURIO_MCP_PAGING_LIMIT": "100"
      }
    }
  }
}
EOF
)
        ;;

    *)
        echo -e "${RED}Invalid option${NC}"
        exit 1
        ;;
esac

# Save configuration
CONFIG_FILE="$SCRIPT_DIR/claude-config/mcp-config.json"
echo "$CLAUDE_CONFIG" > "$CONFIG_FILE"

print_header "Configuration Generated"

echo ""
echo "MCP Server configuration saved to:"
echo -e "${GREEN}  $CONFIG_FILE${NC}"
echo ""
echo "To configure Claude Code, add this to your ~/.claude.json or"
echo "~/.config/claude/settings.json:"
echo ""
echo -e "${YELLOW}$CLAUDE_CONFIG${NC}"
echo ""

# Offer to install automatically
echo ""
read -p "Would you like to add this to your Claude Code configuration? [y/N]: " INSTALL

if [[ "$INSTALL" =~ ^[Yy]$ ]]; then
    CLAUDE_SETTINGS="$HOME/.claude.json"

    if [ -f "$CLAUDE_SETTINGS" ]; then
        print_step "Backing up existing configuration..."
        cp "$CLAUDE_SETTINGS" "$CLAUDE_SETTINGS.backup"
    fi

    # Check if file exists and has mcpServers
    if [ -f "$CLAUDE_SETTINGS" ] && grep -q "mcpServers" "$CLAUDE_SETTINGS"; then
        print_info "Existing mcpServers found. Please merge manually."
        echo "Your new configuration is in: $CONFIG_FILE"
    else
        # Create or update settings
        echo "$CLAUDE_CONFIG" > "$CLAUDE_SETTINGS"
        print_step "Configuration saved to $CLAUDE_SETTINGS"
    fi
fi

print_header "Setup Complete!"

echo ""
echo "Next steps:"
echo "  1. Start the demo environment: docker-compose up -d"
echo "  2. Wait for Ollama model download: docker-compose logs -f ollama-init"
echo "  3. Run the setup script: ./demo-setup.sh"
echo "  4. Restart Claude Code to load the MCP server"
echo "  5. Try: 'List the groups in the registry'"
echo ""
echo "Available MCP tools:"
echo "  - get_server_info, get_artifact_types"
echo "  - list_groups, create_group, search_groups"
echo "  - list_artifacts, create_artifact, search_artifacts"
echo "  - list_versions, create_version, get_version_content"
echo ""
