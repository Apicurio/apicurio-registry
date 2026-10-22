FROM ubuntu:latest

# Install basic tools and dependencies
RUN apt update
RUN apt install -y curl ca-certificates npm
RUN apt install -y gnupg

# Set default working directory
WORKDIR /mnt/apicurio-registry-ui

# Install Playwright globally
RUN npm install -g playwright
RUN npx playwright install-deps

# Copy entrypoint script
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# Set entrypoint and default command
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["bash"]
