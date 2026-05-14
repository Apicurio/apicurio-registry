FROM registry.access.redhat.com/ubi10/ubi-minimal:latest

RUN microdnf install -y git-core openssh-clients openssh-server shadow-utils && \
    microdnf clean all

# Create a dedicated 'git' user for SSH push operations.
# The user's shell is git-shell to restrict access to git commands only.
RUN useradd -m -d /home/git -s /usr/bin/git-shell git && \
    mkdir -p /home/git/.ssh /home/git/.sshd /repos && \
    chown -R git:git /home/git /repos && \
    chmod 700 /home/git/.ssh

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
COPY ssh_config.template sshd_config.template /usr/local/share/apicurio-gitops/
RUN chmod 755 /usr/local/bin/entrypoint.sh

USER git

VOLUME ["/repos"]

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
