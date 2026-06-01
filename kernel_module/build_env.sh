#!/usr/bin/env bash
# ============================================================================
# build_env.sh - OnePlus ACE5 (SM8650/pineapple) ARM64 Clang 内核编译环境配置
# ============================================================================
# 用途: 在 Ubuntu 22.04 上安装所有编译依赖、下载 Clang/LLVM 工具链、
#       拉取 OnePlus ACE5 内核源码，并配置完整的编译环境变量。
#
# 目标设备: OnePlus ACE5 (SM8650 / pineapple)
# SoC:      Qualcomm Snapdragon 8 Gen 3 (SM8650)
# 架构:     ARM64 (aarch64)
# 内核:     6.1 (Android 14 GKI)
# 构建:     Bazel
#
# 用法:
#   chmod +x build_env.sh
#   ./build_env.sh              # 默认配置，拉取完整源码
#   ./build_env.sh --no-sync    # 仅安装工具链，不拉取源码
#   ./build_env.sh --help       # 显示帮助
# ============================================================================

set -euo pipefail

# ============================================================================
# 配置区域 - 可根据需要修改
# ============================================================================

# 设备与 SoC 配置
export DEVICE_NAME="oneplus_ace5"
export SOC_CODE="pineapple"
export SOC_FAMILY="sm8650"
export ARCH="arm64"
export SUBARCH="arm64"

# 内核版本
export KERNEL_VERSION_MAJOR=6
export KERNEL_VERSION_MINOR=1

# Android 编译配置
export CROSS_COMPILE="aarch64-linux-android-"
export CLANG_TRIPLE="aarch64-linux-gnu-"

# 源码仓库配置
export MANIFEST_REPO="https://github.com/OnePlusOSS/kernel_manifest.git"
export MANIFEST_BRANCH="oneplus/sm8650"
export MANIFEST_FILE="oneplus_ace5.xml"

# 工作目录
export KERNEL_MODULE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export TOOLCHAIN_DIR="${KERNEL_MODULE_ROOT}/toolchain"
export CLANG_DIR="${TOOLCHAIN_DIR}/clang"
export CLANG_VERSION="r450000"

# Clang 工具链下载源 (AOSPA prebuilt, 推荐)
export CLANG_AOSPA_URL="https://github.com/AOSPA/platform_prebuilts_clang_kernel_linux-x86_64"
# 备选: Google 官方
# export CLANG_GOOGLE_URL="https://android.googlesource.com/platform/prebuilts/clang/host/linux-x86"

# GCC 交叉编译器下载源
export GCC_AOSPA_URL="https://github.com/AOSPA/platform_prebuilts_gcc_linux-x86_aarch64_linux-android"
export GCC_DIR="${TOOLCHAIN_DIR}/gcc"

# LLVM 工具链 (用于 Bazel 构建)
export LLVM_DIR="${TOOLCHAIN_DIR}/llvm"

# 源码目录
export KERNEL_SRC_DIR="${KERNEL_MODULE_ROOT}/android-kernel"

# GKI 头文件输出目录
export GKI_HEADERS_DIR="${KERNEL_MODULE_ROOT}/gki_headers"

# ============================================================================

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info()    { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*"; }
log_section() { echo -e "\n${BLUE}==== $* ====${NC}\n"; }

# 显示帮助
show_help() {
    cat << 'EOF'
用法: ./build_env.sh [选项]

选项:
  --no-sync       仅安装工具链和依赖，不拉取内核源码
  --no-deps       跳过依赖安装（仅配置工具链）
  --no-clang      跳过 Clang 工具链下载
  --no-gcc        跳过 GCC 交叉编译器下载
  --clean         清理所有已安装的工具链和源码
  --help, -h      显示此帮助信息

环境变量:
  CLANG_VERSION   Clang 版本号 (默认: r450000)
  KERNEL_SRC_DIR  内核源码目录 (默认: <script_dir>/android-kernel)
  TOOLCHAIN_DIR   工具链目录 (默认: <script_dir>/toolchain)

示例:
  ./build_env.sh                    # 完整安装
  ./build_env.sh --no-sync          # 仅安装工具链
  ./build_env.sh --no-deps --no-gcc # 仅下载 Clang
EOF
    exit 0
}

# 解析命令行参数
SKIP_SYNC=false
SKIP_DEPS=false
SKIP_CLANG=false
SKIP_GCC=false
DO_CLEAN=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --no-sync)   SKIP_SYNC=true  ;;
        --no-deps)    SKIP_DEPS=true  ;;
        --no-clang)   SKIP_CLANG=true ;;
        --no-gcc)     SKIP_GCC=true   ;;
        --clean)      DO_CLEAN=true    ;;
        --help|-h)    show_help        ;;
        *)
            log_error "未知参数: $1"
            show_help
            ;;
    esac
    shift
done

# 清理操作
if [[ "$DO_CLEAN" == "true" ]]; then
    log_section "清理工具链和源码"
    log_info "删除工具链目录: ${TOOLCHAIN_DIR}"
    rm -rf "${TOOLCHAIN_DIR}"
    log_info "删除内核源码目录: ${KERNEL_SRC_DIR}"
    rm -rf "${KERNEL_SRC_DIR}"
    log_info "删除 GKI 头文件目录: ${GKI_HEADERS_DIR}"
    rm -rf "${GKI_HEADERS_DIR}"
    log_info "清理完成"
    exit 0
fi

# ============================================================================
# 步骤 1: 安装编译依赖
# ============================================================================
install_dependencies() {
    log_section "步骤 1/4: 安装编译依赖"

    # 检测系统版本
    if [[ -f /etc/os-release ]]; then
        . /etc/os-release
        log_info "检测到系统: ${NAME} ${VERSION}"
    fi

    # 更新包列表
    log_info "更新包列表..."
    sudo apt-get update -y

    # 安装基础编译工具
    log_info "安装基础编译工具..."
    sudo apt-get install -y \
        build-essential \
        bc \
        bison \
        flex \
        libssl-dev \
        libelf-dev \
        libncurses-dev \
        libncurses5-dev \
        libbz2-dev \
        liblz4-tool \
        liblzma-dev \
        libzstd-dev \
        zstd \
        libc6-dev \
        binutils \
        ca-certificates \
        curl \
        wget \
        git \
        gnupg \
        lsb-release \
        software-properties-common \
        python3 \
        python3-pip \
        python3-setuptools \
        python3-wheel \
        file \
        cpio \
        rsync \
        unzip \
        xz-utils \
        device-tree-compiler \
        ccache \
        ninja-build \
        pkg-config \
        dos2unix \
        xmlstarlet \
        jq

    # 安装 repo 工具
    log_info "安装 repo 工具..."
    REPO_BIN="${KERNEL_MODULE_ROOT}/bin"
    mkdir -p "${REPO_BIN}"
    if [[ ! -x "${REPO_BIN}/repo" ]]; then
        curl -L https://storage.googleapis.com/git-repo-downloads/repo \
            -o "${REPO_BIN}/repo"
        chmod +x "${REPO_BIN}/repo"
        log_info "repo 工具已安装到: ${REPO_BIN}/repo"
    else
        log_info "repo 工具已存在，跳过安装"
    fi

    # 安装 Bazel (内核构建需要)
    log_info "安装 Bazel 构建工具..."
    if ! command -v bazel &>/dev/null; then
        # 使用 bazelisk 作为版本管理器
        npm install -g @bazel/bazelisk 2>/dev/null || {
            # 备选方案: 直接下载 Bazel
            BAZEL_VERSION="6.4.0"
            log_info "通过直接下载安装 Bazel ${BAZEL_VERSION}..."
            curl -L "https://github.com/bazelbuild/bazel/releases/download/${BAZEL_VERSION}/bazel-${BAZEL_VERSION}-linux-x86_64" \
                -o /usr/local/bin/bazel
            chmod +x /usr/local/bin/bazel
        }
        log_info "Bazel 安装完成: $(bazel --version 2>/dev/null || echo 'unknown')"
    else
        log_info "Bazel 已安装: $(bazel --version)"
    fi

    # 安装 Python 依赖
    log_info "安装 Python 依赖..."
    pip3 install --user \
        pyyaml \
        configparser \
        google-api-python-client \
        oauth2client \
        pyelftools \
        2>/dev/null || true

    log_info "编译依赖安装完成"
}

# ============================================================================
# 步骤 2: 下载 Clang/LLVM 工具链
# ============================================================================
install_clang_toolchain() {
    log_section "步骤 2/4: 下载 Clang/LLVM 工具链"

    mkdir -p "${CLANG_DIR}"

    if [[ -d "${CLANG_DIR}/bin" && -x "${CLANG_DIR}/bin/clang" ]]; then
        local installed_version
        installed_version=$("${CLANG_DIR}/bin/clang" --version 2>/dev/null | head -1 || echo "unknown")
        log_info "Clang 已安装: ${installed_version}"
        log_info "路径: ${CLANG_DIR}/bin/clang"
        log_warn "如需重新安装，请先运行: $0 --clean"
        return 0
    fi

    log_info "从 AOSPA 下载 Clang/LLVM 工具链..."
    log_info "目标版本: ${CLANG_VERSION}"

    # 尝试从 AOSPA 下载
    if git clone --depth 1 "${CLANG_AOSPA_URL}" "${CLANG_DIR}" 2>/dev/null; then
        log_info "Clang 工具链下载完成 (AOSPA)"
    else
        log_warn "AOSPA 下载失败，尝试 Google 官方源..."

        # 备选方案: 从 Google Android 源下载
        if git clone --depth 1 --single-branch \
            "https://android.googlesource.com/platform/prebuilts/clang/host/linux-x86" \
            "${CLANG_DIR}" 2>/dev/null; then
            log_info "Clang 工具链下载完成 (Google)"
        else
            log_error "Clang 工具链下载失败！"
            log_error "请手动下载并放置到: ${CLANG_DIR}"
            exit 1
        fi
    fi

    # 验证安装
    if [[ -x "${CLANG_DIR}/bin/clang" ]]; then
        local clang_version
        clang_version=$("${CLANG_DIR}/bin/clang" --version 2>/dev/null | head -1)
        log_info "Clang 安装验证成功: ${clang_version}"
    else
        log_error "Clang 二进制文件未找到: ${CLANG_DIR}/bin/clang"
        log_error "请检查工具链目录结构"
        exit 1
    fi
}

# ============================================================================
# 步骤 3: 下载 GCC 交叉编译器
# ============================================================================
install_gcc_toolchain() {
    log_section "步骤 3/4: 下载 GCC 交叉编译器"

    mkdir -p "${GCC_DIR}"

    if [[ -d "${GCC_DIR}/bin" && -x "${GCC_DIR}/bin/${CROSS_COMPILE}gcc" ]]; then
        log_info "GCC 交叉编译器已安装: ${GCC_DIR}/bin/${CROSS_COMPILE}gcc"
        return 0
    fi

    log_info "从 AOSPA 下载 GCC aarch64 交叉编译器..."

    if git clone --depth 1 "${GCC_AOSPA_URL}" "${GCC_DIR}" 2>/dev/null; then
        log_info "GCC 交叉编译器下载完成"
    else
        log_warn "AOSPA GCC 下载失败，尝试 Google 官方源..."
        if git clone --depth 1 --single-branch \
            "https://android.googlesource.com/platform/prebuilts/gcc/linux-x86/aarch64/aarch64-linux-android" \
            "${GCC_DIR}" 2>/dev/null; then
            log_info "GCC 交叉编译器下载完成 (Google)"
        else
            log_warn "GCC 交叉编译器下载失败，将仅使用 Clang/LLVM"
            log_warn "部分内核模块可能需要 GCC，建议手动安装"
        fi
    fi

    # 验证安装
    if [[ -x "${GCC_DIR}/bin/${CROSS_COMPILE}gcc" ]]; then
        log_info "GCC 安装验证成功: $(${GCC_DIR}/bin/${CROSS_COMPILE}gcc --version | head -1)"
    else
        log_warn "GCC 二进制文件未找到，将仅使用 Clang"
    fi
}

# ============================================================================
# 步骤 4: 拉取内核源码
# ============================================================================
sync_kernel_source() {
    log_section "步骤 4/4: 拉取 OnePlus ACE5 内核源码"

    if [[ -d "${KERNEL_SRC_DIR}/.repo" ]]; then
        log_info "内核源码已存在: ${KERNEL_SRC_DIR}"
        log_info "如需重新拉取，请先删除该目录或运行: $0 --clean"
        return 0
    fi

    mkdir -p "${KERNEL_SRC_DIR}"
    cd "${KERNEL_SRC_DIR}"

    log_info "初始化 repo..."
    log_info "  仓库: ${MANIFEST_REPO}"
    log_info "  分支: ${MANIFEST_BRANCH}"
    log_info "  Manifest: ${MANIFEST_FILE}"

    # 使用指定的 manifest 文件初始化
    repo init \
        -u "${MANIFEST_REPO}" \
        -b "${MANIFEST_BRANCH}" \
        -m "${MANIFEST_FILE}" \
        --depth=1 \
        --repo-url="https://storage.googleapis.com/git-repo-downloads/repo"

    log_info "同步源码 (使用所有 CPU 核心)..."
    repo sync \
        -c \
        -j"$(nproc --all)" \
        --fail-fast \
        --no-tags \
        --force-sync \
        --no-clone-bundle \
        --optimized-fetch \
        --prune \
        --current-branch

    log_info "内核源码拉取完成"
    log_info "源码目录: ${KERNEL_SRC_DIR}"

    # 显示源码结构概览
    echo ""
    log_info "源码结构:"
    ls -la "${KERNEL_SRC_DIR}/" | head -20
}

# ============================================================================
# 配置环境变量 (写入 env.sh 供后续脚本 source)
# ============================================================================
setup_env_file() {
    log_section "生成环境配置文件"

    local env_file="${KERNEL_MODULE_ROOT}/env.sh"

    cat > "${env_file}" << ENVEOF
#!/usr/bin/env bash
# ============================================================================
# env.sh - 自动生成的内核编译环境变量
# 由 build_env.sh 生成，请勿手动编辑
# 用法: source env.sh
# ============================================================================

# 设备信息
export DEVICE_NAME="${DEVICE_NAME}"
export SOC_CODE="${SOC_CODE}"
export SOC_FAMILY="${SOC_FAMILY}"

# 架构
export ARCH="${ARCH}"
export SUBARCH="${SUBARCH}"

# 工具链路径
export CLANG_DIR="${CLANG_DIR}"
export GCC_DIR="${GCC_DIR}"
export TOOLCHAIN_DIR="${TOOLCHAIN_DIR}"

# 交叉编译前缀
export CROSS_COMPILE="\${CLANG_DIR}/bin/${CROSS_COMPILE}"
export CLANG_TRIPLE="${CLANG_TRIPLE}"
export CROSS_COMPILE_ARM64="\${CLANG_DIR}/bin/${CROSS_COMPILE}"
export CROSS_COMPILE_ARM32="\${CLANG_DIR}/bin/arm-linux-androideabi-"

# Clang/LLVM 配置
export LLVM_DIR="\${CLANG_DIR}"
export LLVM=\$(which llvm 2>/dev/null || echo "\${CLANG_DIR}/bin/llvm")
export CC="\${CLANG_DIR}/bin/clang"
export LD="\${CLANG_DIR}/bin/ld.lld"
export AR="\${CLANG_DIR}/bin/llvm-ar"
export NM="\${CLANG_DIR}/bin/llvm-nm"
export OBJCOPY="\${CLANG_DIR}/bin/llvm-objcopy"
export OBJDUMP="\${CLANG_DIR}/bin/llvm-objdump"
export STRIP="\${CLANG_DIR}/bin/llvm-strip"

# GCC 交叉编译器 (备用)
if [[ -x "\${GCC_DIR}/bin/${CROSS_COMPILE}gcc" ]]; then
    export CROSS_COMPILE_GCC="\${GCC_DIR}/bin/${CROSS_COMPILE}"
fi

# PATH 设置
export PATH="\${CLANG_DIR}/bin:\${GCC_DIR}/bin:\${KERNEL_MODULE_ROOT}/bin:\${PATH}"

# 源码和输出目录
export KERNEL_SRC_DIR="${KERNEL_SRC_DIR}"
export GKI_HEADERS_DIR="${GKI_HEADERS_DIR}"
export KERNEL_MODULE_ROOT="${KERNEL_MODULE_ROOT}"

# 编译优化选项
export KBUILD_BUILD_USER="builder"
export KBUILD_BUILD_HOST="aurorasu-build"
export LOCALVERSION="-AuroraSU-${DEVICE_NAME}"

# Clang 编译标志
export KCFLAGS="-O2 -Wno-error"
export KCPPFLAGS=""

# ccache 配置 (加速重复编译)
export USE_CCACHE=1
export CCACHE_EXEC=\$(which ccache 2>/dev/null || echo "")
export CCACHE_DIR="${TOOLCHAIN_DIR}/ccache"

# Bazel 配置
export BAZEL_JAVA_OPT="-Xmx8g"

echo "[ENV] 内核编译环境已加载"
echo "[ENV] 设备: \${DEVICE_NAME} (\${SOC_CODE})"
echo "[ENV] 架构: \${ARCH}"
echo "[ENV] Clang: \${CC}"
echo "[ENV] 源码: \${KERNEL_SRC_DIR}"
ENVEOF

    chmod +x "${env_file}"
    log_info "环境配置文件已生成: ${env_file}"
}

# ============================================================================
# 主流程
# ============================================================================
main() {
    echo ""
    echo "============================================"
    echo "  OnePlus ACE5 内核编译环境配置"
    echo "  SoC: SM8650 (pineapple)"
    echo "  内核: 6.1 (Android 14 GKI)"
    echo "  构建: Bazel + Clang/LLVM"
    echo "============================================"
    echo ""

    # 检查运行环境
    if [[ "$(uname -m)" != "x86_64" ]]; then
        log_warn "当前架构: $(uname -m)，建议在 x86_64 主机上编译"
    fi

    # 检查磁盘空间 (至少需要 50GB)
    local available_gb
    available_gb=$(df -BG "${KERNEL_MODULE_ROOT}" | awk 'NR==2 {print $4}' | tr -d 'G')
    if [[ "${available_gb}" -lt 50 ]]; then
        log_warn "可用磁盘空间: ${available_gb}GB，建议至少 50GB"
    else
        log_info "可用磁盘空间: ${available_gb}GB"
    fi

    # 执行安装步骤
    if [[ "$SKIP_DEPS" == "false" ]]; then
        install_dependencies
    else
        log_section "跳过依赖安装"
    fi

    if [[ "$SKIP_CLANG" == "false" ]]; then
        install_clang_toolchain
    else
        log_section "跳过 Clang 工具链安装"
    fi

    if [[ "$SKIP_GCC" == "false" ]]; then
        install_gcc_toolchain
    else
        log_section "跳过 GCC 交叉编译器安装"
    fi

    if [[ "$SKIP_SYNC" == "false" ]]; then
        sync_kernel_source
    else
        log_section "跳过源码同步"
    fi

    # 生成环境配置文件
    setup_env_file

    # 最终摘要
    echo ""
    echo "============================================"
    echo "  环境配置完成！"
    echo "============================================"
    echo ""
    echo "  下一步操作:"
    echo ""
    echo "  1. 加载环境变量:"
    echo "     source ${KERNEL_MODULE_ROOT}/env.sh"
    echo ""
    echo "  2. 编译内核:"
    echo "     ./build_kernel.sh"
    echo ""
    echo "  3. 提取 GKI 头文件 (供模块编译):"
    echo "     ./extract_headers.sh"
    echo ""
    echo "  关键配置:"
    echo "    ARCH          = ${ARCH}"
    echo "    CROSS_COMPILE = ${CROSS_COMPILE}"
    echo "    CLANG_TRIPLE  = ${CLANG_TRIPLE}"
    echo "    CLANG_PATH    = ${CLANG_DIR}/bin/clang"
    echo "    KERNEL_SRC    = ${KERNEL_SRC_DIR}"
    echo "    GKI_HEADERS   = ${GKI_HEADERS_DIR}"
    echo ""
}

main "$@"
