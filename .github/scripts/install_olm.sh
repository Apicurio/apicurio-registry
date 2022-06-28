if [ ! -f "./install-olm.sh" ]; then
    curl -L https://github.com/operator-framework/operator-lifecycle-manager/releases/download/v0.21.2/install.sh -o install.sh
    chmod +x install.sh

fi
./install.sh v0.21.2
sleep 5

# Install OPM tool
if ! command -v opm &> /dev/null
then
    curl -o opm -L https://github.com/operator-framework/operator-registry/releases/download/v1.23.0/linux-amd64-opm
    chmod 755 opm
    sudo mv opm /usr/local/bin
fi