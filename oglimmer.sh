#!/usr/bin/env bash

set -euo pipefail

# Define script metadata
SCRIPT_NAME=$(basename "$0")
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Default configuration
DEFAULT_REGISTRIES=("ghcr.io/oglimmer")
DEFAULT_FRONTEND_DEPLOYMENT="status-tacos-frontend"
DEFAULT_BACKEND_DEPLOYMENT="status-tacos-backend"

# Configuration variables (can be overridden by parameters)
REGISTRIES=("${DEFAULT_REGISTRIES[@]}")
FRONTEND_IMAGES=()
BACKEND_IMAGES=()
FRONTEND_DEPLOYMENT="$DEFAULT_FRONTEND_DEPLOYMENT"
BACKEND_DEPLOYMENT="$DEFAULT_BACKEND_DEPLOYMENT"

# Directories
BACKEND_DIR="$SCRIPT_DIR/backend"
FRONTEND_DIR="$SCRIPT_DIR/frontend"

# Default options (can be overridden by environment variables)
BUILD_FRONTEND="${BUILD_FRONTEND:-false}"
BUILD_BACKEND="${BUILD_BACKEND:-false}"
VERBOSE="${VERBOSE:-false}"
DRY_RUN="${DRY_RUN:-false}"
RESTART="${RESTART:-true}"
PUSH="${PUSH:-true}"
HELP=false
PLATFORM="${PLATFORM:-multi}"
# Restart hook — triggers an in-cluster rollout when kubectl is not available
# (e.g. CI build runners with no cluster access). See restart_via_hook().
RESTART_HOOK_URL="${RESTART_HOOK_URL:-https://restart.oglimmer.com/restart}"
K8S_NAMESPACE="${K8S_NAMESPACE:-default}"
RELEASE_MODE=false
SHOW_VERSIONS=false
COPY_STAGE_DB=false
NOTIFIER_RELEASE=false

# Color output (only if terminal supports it)
if [[ -t 1 ]] && command -v tput >/dev/null 2>&1; then
  BOLD="$(tput bold)"
  GREEN="$(tput setaf 2)"
  YELLOW="$(tput setaf 3)"
  RED="$(tput setaf 1)"
  BLUE="$(tput setaf 4)"
  RESET="$(tput sgr0)"
else
  BOLD="" GREEN="" YELLOW="" RED="" BLUE="" RESET=""
fi

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${RESET} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${RESET} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${RESET} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${RESET} $1" >&2
}

# Verbose logging
log_verbose() {
    if [[ "$VERBOSE" == true ]]; then
        echo -e "${BLUE}[VERBOSE]${RESET} $1"
    fi
}

# Execute command with dry-run and verbose support
execute_cmd() {
    local cmd="$1"

    if [[ "$DRY_RUN" == true ]]; then
        echo -e "${YELLOW}[DRY-RUN]${RESET} ${cmd}"
        return 0
    else
        log_verbose "Executing: $cmd"
        if [[ "$VERBOSE" == true ]]; then
            eval "$cmd"
        else
            eval "$cmd" >/dev/null 2>&1
        fi
    fi
}

# Show usage information
show_help() {
    cat << EOF
Usage: ${SCRIPT_NAME} [OPTIONS] [COMMAND]

Build, deploy, and release status-tacos application components.

COMMANDS:
    build               Build and deploy components (default)
    release             Create a new release with version bumping and build
    show                Show current backend and frontend versions
    copy-stage-db       Copy database from stage server to local MariaDB
    notifier            Build, sign (Developer ID) and notarize the macOS notifier app,
                        then zip it for direct sharing (outside the App Store)

BUILD OPTIONS:
    -f, --frontend          Build and deploy frontend only
    -b, --backend           Build and deploy backend only
    -a, --all               Build and deploy both frontend and backend (default if no component specified)
    -v, --verbose           Enable verbose output
    -n, --no-restart        Skip Kubernetes deployment restart
    --no-push               Skip pushing images to registry
    --dry-run               Show what would be done without executing

    # Registry configuration options
    --registries "REG1,REG2"    Comma-separated list of registries to push to (default: ${DEFAULT_REGISTRIES[0]})
                               Images will be tagged as REGISTRY/status-tacos-frontend and REGISTRY/status-tacos-backend
    --frontend-deploy NAME      Frontend deployment name (default: $DEFAULT_FRONTEND_DEPLOYMENT)
    --backend-deploy NAME       Backend deployment name (default: $DEFAULT_BACKEND_DEPLOYMENT)

    # Platform options
    --platform PLATFORM        Target platform(s) for Docker build:
                               - amd64: Build for AMD64/x86_64 architecture
                               - arm64: Build for ARM64 architecture
                               - multi: Build for both amd64 and arm64 (multi-platform, default)
                               - auto: Detect current platform automatically

    -h, --help              Show this help message

EXAMPLES:
    ${SCRIPT_NAME}                                          # Build and deploy both components with defaults
    ${SCRIPT_NAME} build -f                                 # Build and deploy frontend only
    ${SCRIPT_NAME} build -b -v                              # Build and deploy backend with verbose output
    ${SCRIPT_NAME} release                                  # Create a new release with version bump and build
    ${SCRIPT_NAME} show                                     # Show current versions
    ${SCRIPT_NAME} copy-stage-db                            # Copy database from stage server to local MariaDB
    ${SCRIPT_NAME} notifier                                 # Notarized zip of the macOS app in notifier-app/build/
    ${SCRIPT_NAME} build --registries my-registry.com                               # Use custom registry
    ${SCRIPT_NAME} build --registries "reg1.com,reg2.com"                          # Push to multiple registries
    ${SCRIPT_NAME} build --registries localhost:5000 --no-push                     # Use local registry without pushing
    ${SCRIPT_NAME} build --platform amd64                  # Build for AMD64 only

ENVIRONMENT VARIABLES:
    FRONTEND_DEPLOYMENT     Override default frontend deployment name
    BACKEND_DEPLOYMENT      Override default backend deployment name
    PLATFORM                Override default platform (amd64|arm64|multi|auto)
    DEFAULT_REGISTRIES_ENV  Override default registries (comma-separated)
    IMAGE_TAG               Image tag to push (default: none, i.e. latest), e.g. latest-arm64
    VERBOSE                 Enable verbose mode (true/false)
    DRY_RUN                 Enable dry-run mode (true/false)
    PUSH                    Enable/disable pushing to registry (true/false)
    RESTART                 Enable/disable Kubernetes restart (true/false)
    RESTART_TOKEN           Bearer token for the restart hook. Used to restart
                            deployments when kubectl is unavailable (e.g. CI).
    RESTART_HOOK_URL        Restart hook base URL (default: https://restart.oglimmer.com/restart)
    K8S_NAMESPACE           Namespace for restart-hook rollouts (default: default)
    NOTARIZE_TIMEOUT        Seconds to wait for Apple notarization (default: 1200)

EOF
}

# Parse command line arguments
parse_args() {
    # Check if first argument is a command
    if [[ $# -gt 0 ]]; then
        case $1 in
            build)
                shift
                ;;
            release)
                RELEASE_MODE=true
                shift
                ;;
            show)
                SHOW_VERSIONS=true
                shift
                ;;
            copy-stage-db)
                COPY_STAGE_DB=true
                shift
                ;;
            notifier)
                NOTIFIER_RELEASE=true
                shift
                ;;
            help|-h|--help)
                HELP=true
                shift
                ;;
        esac
    fi

    while [[ $# -gt 0 ]]; do
        case $1 in
            -f|--frontend)
                BUILD_FRONTEND=true
                shift
                ;;
            -b|--backend)
                BUILD_BACKEND=true
                shift
                ;;
            -a|--all)
                BUILD_FRONTEND=true
                BUILD_BACKEND=true
                shift
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -n|--no-restart)
                RESTART=false
                shift
                ;;
            --no-push)
                PUSH=false
                shift
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --registries)
                # Clear existing registries and parse comma-separated list
                REGISTRIES=()
                IFS=',' read -ra ADDR <<< "$2"
                for registry in "${ADDR[@]}"; do
                    REGISTRIES+=("$(echo "$registry" | xargs)")  # trim whitespace
                done
                shift 2
                ;;
            --frontend-deploy)
                FRONTEND_DEPLOYMENT="$2"
                shift 2
                ;;
            --backend-deploy)
                BACKEND_DEPLOYMENT="$2"
                shift 2
                ;;
            --platform)
                PLATFORM="$2"
                shift 2
                ;;
            -h|--help)
                HELP=true
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done

    # Handle environment variable overrides
    FRONTEND_DEPLOYMENT="${FRONTEND_DEPLOYMENT:-$FRONTEND_DEPLOYMENT}"
    BACKEND_DEPLOYMENT="${BACKEND_DEPLOYMENT:-$BACKEND_DEPLOYMENT}"
    PLATFORM="${DOCKER_PLATFORM:-$PLATFORM}"

    # Override default registries from environment if set
    if [[ -n "${DEFAULT_REGISTRIES_ENV:-}" ]]; then
        REGISTRIES=()
        IFS=',' read -ra ADDR <<< "$DEFAULT_REGISTRIES_ENV"
        for registry in "${ADDR[@]}"; do
            REGISTRIES+=("$(echo "$registry" | xargs)")
        done
    fi

    # Build image arrays from registries
    if [[ ${#REGISTRIES[@]} -gt 0 ]]; then
        FRONTEND_IMAGES=()
        BACKEND_IMAGES=()
        for registry in "${REGISTRIES[@]}"; do
            FRONTEND_IMAGES+=("$registry/status-tacos-frontend${IMAGE_TAG:+:$IMAGE_TAG}")
            BACKEND_IMAGES+=("$registry/status-tacos-backend${IMAGE_TAG:+:$IMAGE_TAG}")
        done
    else
        # Fallback to defaults if no registries specified
        FRONTEND_IMAGES=("${DEFAULT_REGISTRIES[0]}/status-tacos-frontend")
        BACKEND_IMAGES=("${DEFAULT_REGISTRIES[0]}/status-tacos-backend")
    fi

    # Validate platform parameter
    if [[ -n "$PLATFORM" && ! "$PLATFORM" =~ ^(amd64|arm64|multi|auto)$ ]]; then
        log_error "Invalid platform: $PLATFORM. Must be one of: amd64, arm64, multi, auto"
        exit 1
    fi

    # Validate conflicting options
    if [[ "$PUSH" == false && "$RESTART" == true && "$RELEASE_MODE" == false ]]; then
        log_warning "Cannot restart deployments without pushing images. Setting --no-restart."
        RESTART=false
    fi

    # If no component specified for build mode, build both
    if [[ "$RELEASE_MODE" == false && "$SHOW_VERSIONS" == false && "$BUILD_FRONTEND" == false && "$BUILD_BACKEND" == false ]]; then
        BUILD_FRONTEND=true
        BUILD_BACKEND=true
    fi
}

# Check if required tools are available
check_prerequisites() {
    local tools=("docker")
    local missing_deps=()

    # Add additional tools for release mode
    if [[ "$RELEASE_MODE" == true ]]; then
        tools+=("mvn" "npm" "git")
    fi

    # Add additional tools for copy stage db mode
    if [[ "$COPY_STAGE_DB" == true ]]; then
        tools=("docker" "ssh")  # Only need docker and ssh for db copy
    fi

    for tool in "${tools[@]}"; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            missing_deps+=("$tool")
        fi
    done

    if [[ ${#missing_deps[@]} -gt 0 ]]; then
        log_error "Missing required dependencies: ${missing_deps[*]}"
        echo "Please install the missing dependencies and try again." >&2
        exit 1
    fi

    # Restarting a deployment needs EITHER kubectl (direct rollout) OR a
    # RESTART_TOKEN (to call the restart hook). Local/dev has kubectl with
    # cluster access; CI build runners have neither and set RESTART_TOKEN
    # instead. Fail early when a restart is requested but neither path exists.
    if [[ "$RESTART" == true ]] && ! command -v kubectl >/dev/null 2>&1 && [[ -z "${RESTART_TOKEN:-}" ]]; then
        log_error "Restart requested but kubectl is not available and RESTART_TOKEN is not set"
        echo "Install kubectl, set RESTART_TOKEN, or pass --no-restart." >&2
        exit 1
    fi

    # Check if Docker daemon is running (skip in dry-run mode)
    if [[ "$DRY_RUN" != true ]] && ! docker info >/dev/null 2>&1; then
        log_error "Docker daemon is not running"
        echo "Please start Docker and try again." >&2
        exit 1
    fi

    # Check if buildx is available for multi-platform builds
    if [[ "$PLATFORM" == "multi" ]]; then
        if ! docker buildx version &> /dev/null; then
            log_error "Docker buildx is required for multi-platform builds but not available"
            log_info "Please install Docker Desktop or enable buildx plugin"
            exit 1
        fi

        # Ensure buildx builder is available
        if ! docker buildx inspect &> /dev/null; then
            log_info "Creating buildx builder instance..."
            docker buildx create --use --name multiplatform-builder 2>/dev/null || true
        fi
    fi

    log_verbose "All required tools are available"
}

# Show current versions
show_versions() {
    # Backend version
    local backend_version
    backend_version=$(mvn -q \
        -Dexec.executable=echo \
        -Dexec.args='${project.version}' \
        --non-recursive exec:exec \
        -f "$BACKEND_DIR/pom.xml")

    # Frontend version
    local frontend_version
    frontend_version=$(grep '"version"' "$FRONTEND_DIR/package.json" | head -1 | sed -E 's/.*"version": *"([^"]+)".*/\1/')

    echo "Backend version: $backend_version"
    echo "Frontend version: $frontend_version"
}

# Bump semantic version
bump_version() {
    local current_version="$1"
    local bump_type="$2"
    # Strip -SNAPSHOT suffix if present
    current_version="${current_version%-SNAPSHOT}"
    IFS='.' read -r major minor patch <<< "$current_version"

    case "$bump_type" in
        major)
            major=$((major + 1)); minor=0; patch=0;
            ;;
        minor)
            minor=$((minor + 1)); patch=0;
            ;;
        bugfix|patch)
            patch=$((patch + 1));
            ;;
        *)
            echo "Unknown bump type: $bump_type" >&2
            exit 1
            ;;
    esac
    echo "$major.$minor.$patch"
}

# Get platform arguments for docker build
get_platform_args() {
    local platform_args=""

    case "$PLATFORM" in
        "amd64")
            platform_args="--platform linux/amd64"
            ;;
        "arm64")
            platform_args="--platform linux/arm64"
            ;;
        "multi")
            platform_args="--platform linux/amd64,linux/arm64"
            ;;
        "auto"|"")
            # Let Docker detect the platform automatically
            platform_args=""
            ;;
    esac

    echo "$platform_args"
}

# Build Docker image for multiple targets
build_image() {
    local component="$1"
    local dockerfile_args="$2"
    local platform_args=$(get_platform_args)

    # Create array of image tags - passed as remaining arguments
    shift 2
    local image_tags=("$@")
    local primary_tag="${image_tags[0]}"

    log_info "Building $component image for ${#image_tags[@]} target(s):"
    for tag in "${image_tags[@]}"; do
        log_info "  - $tag"
    done
    if [[ -n "$platform_args" ]]; then
        log_info "Target platform(s): $PLATFORM"
    fi

    local build_cmd=""
    local use_buildx=false

    # Use buildx for multi-platform builds or when platform is specified
    if [[ "$PLATFORM" == "multi" || (-n "$PLATFORM" && "$PLATFORM" != "auto") ]]; then
        use_buildx=true
        build_cmd="docker buildx build $platform_args"

        # Add all tags
        for tag in "${image_tags[@]}"; do
            build_cmd="$build_cmd --tag $tag"
        done

        if [[ "$PUSH" == true ]]; then
            build_cmd="$build_cmd --push"
        else
            # For local builds with buildx, we need to load the image
            if [[ "$PLATFORM" != "multi" ]]; then
                build_cmd="$build_cmd --load"
            else
                log_warning "Multi-platform builds cannot be loaded locally, forcing push to registry"
                build_cmd="$build_cmd --push"
            fi
        fi

        # Add dockerfile arguments
        build_cmd="$build_cmd $dockerfile_args"

    else
        # Use regular docker build for single platform or auto-detection
        # Build with primary tag first
        build_cmd="docker build $platform_args --tag $primary_tag $dockerfile_args"

        # Tag for additional registries
        if [[ ${#image_tags[@]} -gt 1 ]]; then
            for tag in "${image_tags[@]:1}"; do
                build_cmd="$build_cmd && docker tag $primary_tag $tag"
            done
        fi

        # Push to all registries if requested
        if [[ "$PUSH" == true ]]; then
            for tag in "${image_tags[@]}"; do
                build_cmd="$build_cmd && docker push $tag"
            done
        fi
    fi

    log_verbose "Build command: $build_cmd"

    if execute_cmd "$build_cmd"; then
        log_success "$component image built successfully"
        if [[ "$PUSH" == false && "$PLATFORM" != "multi" ]]; then
            log_info "$component image tagged locally (not pushed)"
        elif [[ "$PUSH" == true ]]; then
            log_success "$component image pushed to ${#image_tags[@]} target(s)"
        fi
    else
        log_error "Failed to build $component image"
        exit 1
    fi
}

# Restart a deployment via the in-cluster restart hook (POST authenticated
# with RESTART_TOKEN). Used when kubectl is unavailable, e.g. CI runners that
# can't reach the cluster directly. The token is never echoed, even in
# dry-run/verbose mode.
restart_via_hook() {
    local deployment="$1"
    local url="${RESTART_HOOK_URL}/${K8S_NAMESPACE}/${deployment}"

    log_info "Restarting $deployment via hook: $url"

    if [[ "$DRY_RUN" == true ]]; then
        echo -e "${YELLOW}[DRY-RUN]${RESET} curl -fsS -X POST -H 'Authorization: Bearer ***' $url"
        return 0
    fi

    if ! curl -fsS -X POST -H "Authorization: Bearer ${RESTART_TOKEN}" "$url" >/dev/null; then
        log_error "Failed to trigger restart for $deployment via hook"
        exit 1
    fi
    log_success "Deployment $deployment restart triggered via hook"
}

# Restart Kubernetes deployment. Prefer kubectl when present (local/dev with
# cluster access); otherwise fall back to the restart hook using RESTART_TOKEN
# (CI runners without cluster access).
restart_deployment() {
    local deployment="$1"

    if ! command -v kubectl >/dev/null 2>&1; then
        restart_via_hook "$deployment"
        return
    fi

    log_info "Restarting deployment: $deployment"

    if execute_cmd "kubectl rollout restart deployment/$deployment"; then
        log_success "Deployment $deployment restarted successfully"

        # Wait for rollout to complete if verbose
        if [[ "$VERBOSE" == true ]]; then
            log_info "Waiting for rollout to complete..."
            kubectl rollout status deployment/"$deployment" --timeout=300s
        fi
    else
        log_error "Failed to restart deployment: $deployment"
        exit 1
    fi
}

# Execute build process
execute_build() {
    # Display configuration
    echo -e "${BOLD}=== Build Configuration ===${RESET}"
    echo "Registries:        ${REGISTRIES[*]}"
    echo "Platform:          ${PLATFORM:-auto}"
    echo "Build Frontend:    $BUILD_FRONTEND"
    echo "Build Backend:     $BUILD_BACKEND"
    echo "Push to Registry:  $PUSH"
    echo "Restart K8s:       $RESTART"
    echo "Dry-run:           $DRY_RUN"
    echo "Verbose:           $VERBOSE"
    if [[ "$BUILD_FRONTEND" == true ]]; then
        echo "Frontend Deploy:   $FRONTEND_DEPLOYMENT"
    fi
    if [[ "$BUILD_BACKEND" == true ]]; then
        echo "Backend Deploy:    $BACKEND_DEPLOYMENT"
    fi
    echo -e "${BOLD}===========================${RESET}"
    echo

    log_info "Starting build process..."

    # Build frontend
    if [[ "$BUILD_FRONTEND" == true ]]; then
        # Extract version information for frontend build
        log_info "Extracting version information for frontend build..."
        GIT_VERSION=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
        APP_VERSION=$(cd frontend && npm pkg get version | sed 's/"//g' 2>/dev/null || echo "unknown")

        log_verbose "Git version: $GIT_VERSION"
        log_verbose "App version: $APP_VERSION"

        # Build with version environment variables
        build_image "frontend" "frontend --build-arg VITE_GIT_VERSION=$GIT_VERSION --build-arg VITE_APP_VERSION=$APP_VERSION" "${FRONTEND_IMAGES[@]}"
    fi

    # Build backend
    if [[ "$BUILD_BACKEND" == true ]]; then
        build_image "backend" ". -f backend/Dockerfile" "${BACKEND_IMAGES[@]}"
    fi

    # Restart deployments if requested
    if [[ "$RESTART" == true ]]; then
        if [[ "$BUILD_FRONTEND" == true ]]; then
            restart_deployment "$FRONTEND_DEPLOYMENT"
        fi

        if [[ "$BUILD_BACKEND" == true ]]; then
            restart_deployment "$BACKEND_DEPLOYMENT"
        fi
    else
        log_info "Skipping deployment restarts (--no-restart specified)"
    fi

    echo
    echo -e "${BOLD}${GREEN}✓ All operations completed successfully${RESET}"
}

# Copy database from stage server to local MariaDB
copy_stage_database() {
    log_info "Copying database from stage.modular-design.de..."

    # Stage server details
    local stage_server="stage.modular-design.de"
    local stage_db_user="status-tacos"
    local stage_db_password=${STAGE_DB_PASSWORD}
    local stage_db_name="status-tacos"

    # Local database details from compose.yml
    local local_db_user="status-tacos"
    local local_db_password="foobar"
    local local_db_name="status-tacos"

    # Create temporary file for the dump
    local dump_file="/tmp/stage_db_dump_$(date +%Y%m%d_%H%M%S).sql"

    log_info "Creating database dump from stage server..."
    if ssh "$stage_server" "cd /opt/status-tacos-dev && sudo docker compose exec status-tacos-database mariadb-dump -u$stage_db_user -p$stage_db_password --single-transaction --skip-lock-tables $stage_db_name" > "$dump_file"; then
        log_success "Database dump created successfully"
    else
        log_error "Failed to create database dump from stage server"
        rm -f "$dump_file"
        exit 1
    fi

    log_info "Checking if local MariaDB container is running..."
    if ! docker compose ps mariadb | grep -q "Up"; then
        log_info "Starting local MariaDB container..."
        docker compose up -d mariadb

        log_info "Waiting for MariaDB to be ready..."
        timeout=60
        while ! docker compose exec mariadb mariadb -u$local_db_user -p$local_db_password -e "SELECT 1" > /dev/null 2>&1; do
            if [ $timeout -le 0 ]; then
                log_error "Timeout waiting for MariaDB to be ready"
                rm -f "$dump_file"
                exit 1
            fi
            sleep 2
            timeout=$((timeout - 2))
        done
    fi

    log_info "Dropping and recreating local database..."
    docker compose exec mariadb mariadb -uroot -proot -e 'DROP DATABASE IF EXISTS `'$local_db_name'`; CREATE DATABASE `'$local_db_name'`;'

    log_info "Importing database dump to local MariaDB..."
    if docker compose exec -T mariadb mariadb -u$local_db_user -p$local_db_password $local_db_name < "$dump_file"; then
        log_success "Database imported successfully"
    else
        log_error "Failed to import database dump"
        rm -f "$dump_file"
        exit 1
    fi

    # Clean up temporary file
    rm -f "$dump_file"

    echo
    echo -e "${BOLD}${GREEN}✓ Stage database copied to local MariaDB successfully${RESET}"
}

# Execute release process
execute_release() {
    log_info "Starting release process..."

    # Show current versions
    echo "Current versions:"; show_versions; echo

    # Explain bump types
    echo "Select which part to bump (semantic versioning):"
    echo "  1) major  - incompatible API changes"
    echo "  2) minor  - backwards-compatible new features"
    echo "  3) bugfix - backwards-compatible bug fixes"
    PS3="Enter choice (1-3): "
    select bump in major minor bugfix; do
        if [[ -n "$bump" ]]; then
            echo "Chosen bump type: $bump"; break
        else
            echo "Invalid choice. Please select 1, 2, or 3.";
        fi
    done

    # Compute new version
    current_version=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec -f "$BACKEND_DIR/pom.xml")
    new_version=$(bump_version "$current_version" "$bump")
    log_info "Releasing version $new_version..."

    # Update backend to release version
    log_info "Updating backend version to $new_version..."
    mvn versions:set -DnewVersion="$new_version" -DgenerateBackupPoms=false -f "$BACKEND_DIR/pom.xml"

    # Update frontend
    log_info "Updating frontend version to $new_version..."
    (cd "$FRONTEND_DIR" && npm version "$new_version" --no-git-tag-version)

    # Commit and tag release
    log_info "Committing version changes and creating tag..."
    git add "$BACKEND_DIR/pom.xml" "$FRONTEND_DIR/package.json" "$FRONTEND_DIR/package-lock.json"
    git commit -m "Release v$new_version"
    git tag -a "v$new_version" -m "Release v$new_version"

    # Build and upload after version commit
    log_info "Building and uploading release version $new_version..."
    BUILD_FRONTEND=true
    BUILD_BACKEND=true
    execute_build

    # Bump backend to SNAPSHOT
    log_info "Setting backend to SNAPSHOT version..."
    snapshot="${new_version}-SNAPSHOT"
    mvn versions:set -DnewVersion="$snapshot" -DgenerateBackupPoms=false -f "$BACKEND_DIR/pom.xml"
    git add "$BACKEND_DIR/pom.xml"
    git commit -m "Set backend to $snapshot"

    log_success "Release v$new_version complete. Backend is now $snapshot."
}

# Build, sign and notarize the macOS notifier app for direct sharing.
# Uses the Developer ID certificate and the Apple account signed in to Xcode,
# so no notarytool credentials are needed. Bump the version in Xcode first.
release_notifier() {
    local project_dir="$SCRIPT_DIR/notifier-app"
    local build_dir="$project_dir/build"
    local team_id="SBFZ9G94BG"
    local archive="$build_dir/StatusTacosAlert.xcarchive"
    local timeout="${NOTARIZE_TIMEOUT:-1200}"

    for tool in xcodebuild xcrun ditto spctl; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            log_error "Missing required tool: $tool (run this on a Mac with Xcode)"
            exit 1
        fi
    done

    local version
    version=$(xcodebuild -project "$project_dir/StatusTacosAlert.xcodeproj" -scheme StatusTacosAlert \
        -configuration Release -showBuildSettings 2>/dev/null | awk '$1 == "MARKETING_VERSION" { print $3; exit }')
    if [[ -z "$version" ]]; then
        log_error "Cannot read MARKETING_VERSION of the notifier app"
        exit 1
    fi
    log_info "Notifier app version $version"

    if [[ "$DRY_RUN" == true ]]; then
        echo -e "${YELLOW}[DRY-RUN]${RESET} archive, export (developer-id, upload), wait for notarization, zip"
        return 0
    fi

    rm -rf "$archive" "$build_dir/export" "$build_dir/notarized"
    mkdir -p "$build_dir"

    log_info "Archiving (Release)..."
    if ! xcodebuild -project "$project_dir/StatusTacosAlert.xcodeproj" -scheme StatusTacosAlert \
        -configuration Release -destination 'generic/platform=macOS' \
        -archivePath "$archive" -allowProvisioningUpdates -quiet archive; then
        log_error "Archive failed"
        exit 1
    fi

    cat > "$build_dir/ExportOptions.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>developer-id</string>
    <key>destination</key>
    <string>upload</string>
    <key>teamID</key>
    <string>$team_id</string>
    <key>signingStyle</key>
    <string>automatic</string>
</dict>
</plist>
PLIST

    log_info "Signing with Developer ID and uploading to Apple for notarization..."
    if ! xcodebuild -exportArchive -archivePath "$archive" -exportOptionsPlist "$build_dir/ExportOptions.plist" \
        -exportPath "$build_dir/export" -allowProvisioningUpdates -quiet; then
        log_error "Export / upload failed"
        exit 1
    fi

    log_info "Waiting for Apple notarization (up to ${timeout}s)..."
    local waited=0
    local log_file="$build_dir/notarize.log"
    until xcodebuild -exportNotarizedApp -archivePath "$archive" -exportPath "$build_dir/notarized" >"$log_file" 2>&1; do
        if grep -qi "invalid\|rejected" "$log_file"; then
            log_error "Apple rejected the app. Details:"
            tail -20 "$log_file" >&2
            exit 1
        fi
        if (( waited >= timeout )); then
            log_error "Notarization not done after ${timeout}s. Last output:"
            tail -20 "$log_file" >&2
            exit 1
        fi
        sleep 30
        waited=$((waited + 30))
    done

    local app
    app=$(find "$build_dir/notarized" -maxdepth 1 -name '*.app' -print -quit)
    if [[ -z "$app" ]]; then
        log_error "No app found in $build_dir/notarized"
        exit 1
    fi

    log_info "Checking signature and notarization ticket..."
    if ! xcrun stapler validate "$app" >/dev/null || ! spctl -a -t exec "$app" 2>/dev/null; then
        log_error "Gatekeeper does not accept $app"
        spctl -a -vv -t exec "$app" >&2 || true
        exit 1
    fi

    local zip="$build_dir/StatusTacosAlert-$version.zip"
    rm -f "$zip"
    ditto -c -k --keepParent "$app" "$zip"

    echo
    echo -e "${BOLD}${GREEN}✓ Notarized notifier app ready: $zip${RESET}"
}

# Main execution function
main() {
    # Show help if no arguments provided
    if [[ $# -eq 0 ]]; then
        show_help
        exit 0
    fi

    parse_args "$@"

    if [[ "$HELP" == true ]]; then
        show_help
        exit 0
    fi

    if [[ "$SHOW_VERSIONS" == true ]]; then
        show_versions
        exit 0
    fi

    if [[ "$COPY_STAGE_DB" == true ]]; then
        check_prerequisites
        copy_stage_database
        exit 0
    fi

    if [[ "$NOTIFIER_RELEASE" == true ]]; then
        release_notifier
        exit 0
    fi

    check_prerequisites

    if [[ "$RELEASE_MODE" == true ]]; then
        execute_release
    else
        execute_build
    fi
}

# Run main function with all arguments
main "$@"
