#!/bin/bash

# Configuration
FLINK_DOWNLOAD_BASE="https://dlcdn.apache.org/flink/"
FLINK_VERSION="2.0.0"
SCALA_VERSION="2.12"
FLINK_BASE_NAME="flink-${FLINK_VERSION}"
FLINK_ARCHIVE="${FLINK_BASE_NAME}-bin-scala_${SCALA_VERSION}.tgz"
FLINK_DOWNLOAD_URL="${FLINK_DOWNLOAD_BASE}flink-${FLINK_VERSION}/${FLINK_ARCHIVE}"
TARGET_DIR="target"
FLINK_DIR="${TARGET_DIR}/${FLINK_BASE_NAME}"
SCRIPT_NAME=$( basename $0 )

# Ensure the target directory exists relative to the script's location
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
ABS_TARGET_DIR="${SCRIPT_DIR}/${TARGET_DIR}"
ABS_FLINK_DIR="${SCRIPT_DIR}/${FLINK_DIR}"
ABS_FLINK_ARCHIVE="${ABS_TARGET_DIR}/${FLINK_ARCHIVE}"

# Function to download and unpack Flink
download_flink() {
  echo "Ensuring Flink ${FLINK_VERSION} is available..."
  echo "Target directory: ${ABS_TARGET_DIR}"
  mkdir -p "${ABS_TARGET_DIR}"

  # Download Flink if it doesn't exist
  if [ ! -f "${ABS_FLINK_ARCHIVE}" ]; then
    echo "Downloading Apache Flink from ${FLINK_DOWNLOAD_URL}..."
    curl -L -o "${ABS_FLINK_ARCHIVE}" "${FLINK_DOWNLOAD_URL}"
    if [ $? -ne 0 ]; then
      echo "Error downloading Flink. Exiting."
      rm -f "${ABS_FLINK_ARCHIVE}" # Clean up partial download
      exit 1
    fi
  else
    echo "Flink archive already exists: ${ABS_FLINK_ARCHIVE}"
  fi

  # Unpack Flink if it hasn't been unpacked
  if [ ! -d "${ABS_FLINK_DIR}" ]; then
    echo "Unpacking Flink from ${ABS_FLINK_ARCHIVE} into ${ABS_TARGET_DIR}..."
    tar -xzf "${ABS_FLINK_ARCHIVE}" -C "${ABS_TARGET_DIR}"
    if [ $? -ne 0 ]; then
      echo "Error unpacking Flink. Exiting."
      rm -rf "${ABS_FLINK_DIR}" # Clean up potentially corrupted directory
      exit 1
    fi
    echo "Flink unpacked to ${ABS_FLINK_DIR}"
  else
    echo "Flink directory already exists: ${ABS_FLINK_DIR}"
  fi
  echo "Flink download/unpack process complete."
}

# Function to start Flink cluster
start_flink() {
  download_flink # Ensure Flink is ready
  if [ -d "${ABS_FLINK_DIR}" ]; then
    echo "Starting Flink cluster..."
    "${ABS_FLINK_DIR}/bin/start-cluster.sh"
    echo "Flink cluster started."
    echo "To stop the cluster, run: $SCRIPT_NAME stop"
  else
    echo "Flink directory ${ABS_FLINK_DIR} not found. Cannot start cluster."
    exit 1
  fi
}

# Function to stop Flink cluster
stop_flink() {
  if [ -d "${ABS_FLINK_DIR}" ]; then
    echo "Stopping Flink cluster..."
    "${ABS_FLINK_DIR}/bin/stop-cluster.sh"
    echo "Flink cluster stopped."
  else
    echo "Flink directory ${ABS_FLINK_DIR} not found. Cannot stop cluster (maybe not started or unpacked?)."
    # Exit with 0 as stopping a non-existent cluster isn't necessarily an error in all contexts
    exit 0
  fi
}

# Main script logic: Parse command-line arguments
COMMAND=$1

if [ -z "$COMMAND" ]; then
  echo "Usage: $SCRIPT_NAME {download|start|stop|clean}"
  exit 1
fi

case $COMMAND in
  clean)
    rm -rf $ABS_TARGET_DIR
    ;;
  download)
    download_flink
    ;;
  start)
    start_flink
    ;;
  stop)
    stop_flink
    ;;
  *)
    echo "Invalid command: $COMMAND"
    echo "Usage: $SCRIPT_NAME {download|start|stop}"
    exit 1
    ;;
esac

exit 0
