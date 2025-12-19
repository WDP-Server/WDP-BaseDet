#!/bin/bash

# WDP-BaseDet Deployment Script
# Builds and deploys WDP-BaseDet plugin to the test server

# Configuration
PROJECT_DIR="/root/WDP-Rework/WDP-BaseDet"
CONTAINER_ID="b8f24891-b5be-4847-a96e-c705c500aece"
SERVER_DIR="/var/lib/pterodactyl/volumes/${CONTAINER_ID}"
PLUGINS_DIR="${SERVER_DIR}/plugins"
JAR_NAME="WDP-BaseDet.jar"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Function to print colored output
print_step() {
    echo -e "${BLUE}==>${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_header() {
    echo -e ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}           WDP-BaseDet Deployment Script                      ${CYAN}║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo -e ""
}

# Check if running as root
if [ "$EUID" -ne 0 ]; then 
    print_error "This script must be run as root"
    exit 1
fi

print_header

# Step 1: Build the project
print_step "Building WDP-BaseDet project..."
cd "$PROJECT_DIR" || exit 1

# Clean and build with Maven
mvn clean package -DskipTests > /tmp/wdp_basedet_build.log 2>&1

if [ $? -eq 0 ]; then
    print_success "Build completed successfully"
else
    print_error "Build failed! Check /tmp/wdp_basedet_build.log for details"
    echo ""
    print_step "Last 50 lines of build log:"
    tail -n 50 /tmp/wdp_basedet_build.log
    exit 1
fi

# Find the JAR file
JAR_PATH=$(find "${PROJECT_DIR}/target" -name "WDP-BaseDet-*.jar" ! -name "*-shaded.jar" ! -name "*original*" 2>/dev/null | head -1)

if [ -z "$JAR_PATH" ]; then
    # Try to find the shaded JAR
    JAR_PATH=$(find "${PROJECT_DIR}/target" -name "WDP-BaseDet-*.jar" 2>/dev/null | head -1)
fi

if [ -z "$JAR_PATH" ] || [ ! -f "$JAR_PATH" ]; then
    print_error "Built JAR not found in ${PROJECT_DIR}/target/"
    ls -la "${PROJECT_DIR}/target/" 2>/dev/null
    exit 1
fi

print_success "JAR file found: $(basename $JAR_PATH)"

# Step 2: Stop the container
print_step "Stopping Pterodactyl container..."

# Get docker container name from pterodactyl container ID
DOCKER_CONTAINER=$(docker ps -a --format '{{.Names}}' | grep -i "${CONTAINER_ID:0:12}" | head -1)

if [ -z "$DOCKER_CONTAINER" ]; then
    # Try using the container ID directly
    DOCKER_CONTAINER="${CONTAINER_ID:0:12}"
fi

# Check if container is running
if docker ps -q --filter "name=${DOCKER_CONTAINER}" | grep -q . 2>/dev/null || docker ps -q --filter "id=${CONTAINER_ID:0:12}" | grep -q . 2>/dev/null; then
    docker stop "$DOCKER_CONTAINER" > /dev/null 2>&1 || docker stop "${CONTAINER_ID:0:12}" > /dev/null 2>&1
    
    print_step "Waiting for container to stop..."
    STOP_TIMEOUT=30
    STOP_COUNTER=0
    
    while docker ps -q --filter "name=${DOCKER_CONTAINER}" | grep -q . 2>/dev/null || docker ps -q --filter "id=${CONTAINER_ID:0:12}" | grep -q . 2>/dev/null; do
        sleep 1
        STOP_COUNTER=$((STOP_COUNTER + 1))
        
        if [ $STOP_COUNTER -ge $STOP_TIMEOUT ]; then
            print_warning "Container did not stop within ${STOP_TIMEOUT} seconds!"
            print_step "Forcing container stop..."
            docker kill "$DOCKER_CONTAINER" > /dev/null 2>&1 || docker kill "${CONTAINER_ID:0:12}" > /dev/null 2>&1
            sleep 2
            break
        fi
    done
    
    print_success "Container stopped (took ${STOP_COUNTER}s)"
else
    print_warning "Container is already stopped or not found"
fi

# Safety wait for file handles to release
sleep 2

# Step 3: Remove config file from plugins directory
print_step "Removing config file from plugin directory..."
CONFIG_FILE="${PLUGINS_DIR}/WDP-BaseDet/config.yml"
if [ -f "$CONFIG_FILE" ]; then
    rm -f "$CONFIG_FILE"
    print_success "Config file removed: WDP-BaseDet/config.yml"
else
    print_warning "Config file not found (might be first deployment)"
fi

# Step 4: Backup existing plugin (if exists)
if [ -f "${PLUGINS_DIR}/${JAR_NAME}" ]; then
    print_step "Backing up existing plugin..."
    BACKUP_NAME="${JAR_NAME}.backup.$(date +%Y%m%d_%H%M%S)"
    cp "${PLUGINS_DIR}/${JAR_NAME}" "${PLUGINS_DIR}/${BACKUP_NAME}"
    print_success "Backup created: ${BACKUP_NAME}"
fi

# Step 4: Deploy new JAR
print_step "Deploying new JAR to plugins folder..."

# Remove old JARs
rm -f "${PLUGINS_DIR}"/WDP-BaseDet*.jar 2>/dev/null

# Copy new JAR
cp "$JAR_PATH" "${PLUGINS_DIR}/${JAR_NAME}"

if [ $? -eq 0 ]; then
    print_success "Plugin deployed successfully"
else
    print_error "Failed to copy JAR to plugins folder"
    exit 1
fi

# Set permissions
chmod 644 "${PLUGINS_DIR}/${JAR_NAME}"
chown 1000:1000 "${PLUGINS_DIR}/${JAR_NAME}" 2>/dev/null

# Step 5: Start the container
print_step "Starting Pterodactyl container..."

docker start "$DOCKER_CONTAINER" > /dev/null 2>&1 || docker start "${CONTAINER_ID:0:12}" > /dev/null 2>&1

if [ $? -eq 0 ]; then
    print_success "Container started"
else
    print_warning "Could not start container automatically"
    print_step "Please start the server manually from Pterodactyl panel"
fi

# Final summary
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}Deployment Complete!${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "  ${BLUE}Project:${NC}     WDP-BaseDet"
echo -e "  ${BLUE}Version:${NC}     $(grep -m1 '<version>' ${PROJECT_DIR}/pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | tr -d ' ')"
echo -e "  ${BLUE}JAR:${NC}         ${JAR_NAME}"
echo -e "  ${BLUE}Deployed to:${NC} ${PLUGINS_DIR}/"
echo ""
echo -e "  ${YELLOW}Commands to test:${NC}"
echo -e "    /basedet help    - Show help"
echo -e "    /basedet score   - Check your detection score"
echo -e "    /basedet view    - View pending base with particles"
echo -e "    /trust           - Open trust menu"
echo ""
print_success "Ready for testing!"
