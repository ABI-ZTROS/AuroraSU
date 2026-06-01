#!/usr/bin/env bash
# ============================================================================
# build_kernel.sh - OnePlus ACE5 (SM8650/pineapple) 内核编译脚本
# ============================================================================
# 用途: 使用 Clang/LLVM 工具链编译 OnePlus ACE5 内核 (GKI 6.1)
#       支持 Bazel 构建和传统 Makefile 构建两种模式
#
# 前置条件:
#   1. 已运行 build_env.sh 完成环境配置
#   2. 环境变量已通过 source env.sh 加载
#
# 用法:
#   ./build_kernel.sh                  # 默认编译 (Bazel)
#   ./build_kernel.sh --defconfig      # 仅生成 defconfig
#   ./build_kernel.sh --clean          # 清理编译产物
#   ./build_kernel.sh --menuconfig     # 交互式配置
#   ./build_kernel.sh --make           # 使用传统 Makefile 编译
#   ./build_kernel.sh --help           # 显示帮助
# ============================================================================

set -euo pipefail

# ============================================================================
# 脚本所在目录
# ============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KERNEL_MODULE_ROOT="${SCRIPT_DIR}"

# ============================================================================
# 加载环境变量
# ============================================================================
ENV_FILE="${KERNEL_MODULE_ROOT}/env.sh"
if [[ -f "${ENV_FILE}" ]]; then
    source "${ENV_FILE}"
else
    echo "[ERROR] 环境配置文件未找到: ${ENV_FILE}"
    echo "[ERROR] 请先运行: ./build_env.sh"
    exit 1
fi

# ============================================================================
# 编译参数配置
# ============================================================================

# 编译目标配置
DEFCONFIG="pineapple_defconfig"  # OnePlus ACE5 默认配置
# 备选 defconfig (根据实际源码中的文件名调整):
# DEFCONFIG="gki_defconfig"
# DEFCONFIG="pineapple_gki_defconfig"

# 编译线程数 (默认使用所有 CPU 核心)
JOBS="$(nproc --all)"

# LTO 配置 (thin LTO 加速编译)
LTO="thin"

# 输出目录
OUT_DIR="${KERNEL_MODULE_ROOT}/out"
DIST_DIR="${OUT_DIR}/dist"

# 编译日志
BUILD_LOG="${OUT_DIR}/build.log"

# 编译时间记录
BUILD_START_TIME=""

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()    { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()    { echo -e "${CYAN}[STEP]${NC} $*"; }
log_section() { echo -e "\n${BLUE}==== $* ====${NC}\n"; }

# ============================================================================
# 显示帮助
# ============================================================================
show_help() {
    cat << EOF
用法: ./build_kernel.sh [选项]

编译选项:
  --defconfig          仅生成 defconfig，不编译
  --menuconfig         交互式内核配置
  --clean              清理编译产物
  --make               使用传统 Makefile 编译 (非 Bazel)
  --clang-only         仅使用 Clang，不使用 GCC 交叉编译
  --lto=thin           使用 ThinLTO (默认)
  --lto=full           使用 Full LTO
  --lto=none           禁用 LTO
  -j<N>                指定编译线程数 (默认: $(nproc --all))

KSU/AuroraSU 选项:
  --with-ksu           集成 KernelSU (AuroraSU) 到内核
  --ksu-branch=BRANCH  指定 KSU 分支 (默认: dev)
  --ksu-tag=TAG        指定 KSU 版本标签

输出选项:
  --outdir=DIR         指定输出目录 (默认: ${OUT_DIR})
  --verbose            显示详细编译输出
  --help, -h           显示此帮助信息

环境变量 (可在 env.sh 或命令行中设置):
  ARCH              目标架构 (arm64)
  CROSS_COMPILE     交叉编译前缀
  CLANG_TRIPLE      Clang 目标三元组
  CC                C 编译器路径
  LD                链接器路径
  KERNEL_SRC_DIR    内核源码目录

示例:
  ./build_kernel.sh                          # Bazel 编译
  ./build_kernel.sh --make                   # Makefile 编译
  ./build_kernel.sh --with-ksu --ksu-branch dev  # 集成 KSU
  ./build_kernel.sh -j8 --lto=none           # 8线程, 无 LTO
EOF
    exit 0
}

# ============================================================================
# 解析命令行参数
# ============================================================================
DO_DEFCONFIG=false
DO_MENUCONFIG=false
DO_CLEAN=false
USE_MAKE=false
CLANG_ONLY=false
WITH_KSU=false
KSU_BRANCH="dev"
KSU_TAG=""
VERBOSE=false
USER_LTO=""
USER_JOBS=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --defconfig)    DO_DEFCONFIG=true ;;
        --menuconfig)   DO_MENUCONFIG=true ;;
        --clean)        DO_CLEAN=true ;;
        --make)         USE_MAKE=true ;;
        --clang-only)   CLANG_ONLY=true ;;
        --with-ksu)     WITH_KSU=true ;;
        --ksu-branch=*) KSU_BRANCH="${1#*=}" ;;
        --ksu-tag=*)    KSU_TAG="${1#*=}" ;;
        --outdir=*)     OUT_DIR="${1#*=}" ;;
        --verbose)      VERBOSE=true ;;
        --lto=*)        USER_LTO="${1#*=}" ;;
        -j*)            USER_JOBS="${1#-j}" ;;
        --help|-h)      show_help ;;
        *)
            log_error "未知参数: $1"
            show_help
            ;;
    esac
    shift
done

# 覆盖默认值
if [[ -n "${USER_LTO}" ]]; then
    LTO="${USER_LTO}"
fi
if [[ -n "${USER_JOBS}" ]]; then
    JOBS="${USER_JOBS}"
fi

DIST_DIR="${OUT_DIR}/dist"

# ============================================================================
# 前置检查
# ============================================================================
preflight_check() {
    log_section "前置检查"

    # 检查源码目录
    if [[ ! -d "${KERNEL_SRC_DIR}" ]]; then
        log_error "内核源码目录不存在: ${KERNEL_SRC_DIR}"
        log_error "请先运行: ./build_env.sh"
        exit 1
    fi

    # 检查 Clang
    if [[ ! -x "${CLANG_DIR}/bin/clang" ]]; then
        log_error "Clang 未找到: ${CLANG_DIR}/bin/clang"
        log_error "请先运行: ./build_env.sh"
        exit 1
    fi

    # 检查源码构建系统
    if [[ -f "${KERNEL_SRC_DIR}/build/build.sh" ]]; then
        log_info "检测到 build/build.sh 构建脚本"
        BUILD_SYSTEM="build_sh"
    elif [[ -f "${KERNEL_SRC_DIR}/tools/bazel" ]]; then
        log_info "检测到 Bazel 构建系统"
        BUILD_SYSTEM="bazel"
    else
        log_warn "未检测到标准构建系统，将尝试 Makefile"
        BUILD_SYSTEM="makefile"
    fi

    # 检查 defconfig 是否存在
    local defconfig_found=false
    for config_dir in \
        "${KERNEL_SRC_DIR}/arch/${ARCH}/configs" \
        "${KERNEL_SRC_DIR}/common/arch/${ARCH}/configs" \
        "${KERNEL_SRC_DIR}/private/pineapple/arch/${ARCH}/configs"; do
        if [[ -f "${config_dir}/${DEFCONFIG}" ]]; then
            log_info "找到 defconfig: ${config_dir}/${DEFCONFIG}"
            defconfig_found=true
            break
        fi
    done

    if [[ "${defconfig_found}" == "false" ]]; then
        log_warn "默认 defconfig (${DEFCONFIG}) 未找到"
        log_warn "将在编译时尝试自动查找可用的 defconfig"
    fi

    # 创建输出目录
    mkdir -p "${OUT_DIR}" "${DIST_DIR}"

    # 初始化 ccache
    if command -v ccache &>/dev/null; then
        export USE_CCACHE=1
        export CCACHE_DIR="${TOOLCHAIN_DIR}/ccache"
        mkdir -p "${CCACHE_DIR}"
        ccache -M 50G 2>/dev/null || true
        log_info "ccache 已启用 (最大 50GB)"
    fi

    log_info "前置检查通过"
    log_info "  构建系统: ${BUILD_SYSTEM}"
    log_info "  编译线程: ${JOBS}"
    log_info "  LTO: ${LTO}"
    log_info "  输出目录: ${OUT_DIR}"
}

# ============================================================================
# 集成 KernelSU (AuroraSU)
# ============================================================================
setup_kernelsu() {
    log_section "集成 KernelSU (AuroraSU)"

    local ksu_setup_url="https://raw.githubusercontent.com/ZTR-OS/AuroraSU/${KSU_BRANCH}/kernel/setup.sh"
    local ksu_fragment="${KERNEL_SRC_DIR}/aurorasu.fragment"

    log_info "下载并执行 KSU setup 脚本..."
    log_info "  分支: ${KSU_BRANCH}"

    cd "${KERNEL_SRC_DIR}"

    if [[ -n "${KSU_TAG}" ]]; then
        bash <(curl -LSs "${ksu_setup_url}") "${KSU_TAG}"
    else
        bash <(curl -LSs "${ksu_setup_url}") -p "${KSU_BRANCH}"
    fi

    # 创建 KSU defconfig fragment
    cat > "${ksu_fragment}" << 'EOF'
# KernelSU (AuroraSU) 配置
CONFIG_KSU=y
CONFIG_KSU_DEBUG=y
EOF

    log_info "KSU fragment 已创建: ${ksu_fragment}"
}

# ============================================================================
# 清理编译产物
# ============================================================================
do_clean() {
    log_section "清理编译产物"

    cd "${KERNEL_SRC_DIR}"

    if [[ "${BUILD_SYSTEM}" == "bazel" ]]; then
        log_info "清理 Bazel 缓存..."
        bazel clean --expunge 2>/dev/null || true
    fi

    log_info "清理输出目录..."
    rm -rf "${OUT_DIR}"

    # 清理内核源码中的编译产物
    if [[ -f "Makefile" ]]; then
        make mrproper 2>/dev/null || true
    fi

    log_info "清理完成"
}

# ============================================================================
# 使用 Bazel 构建 (推荐)
# ============================================================================
build_with_bazel() {
    log_section "Bazel 构建"

    cd "${KERNEL_SRC_DIR}"

    # 构建 fragment 参数
    local bazel_fragment_args=""
    if [[ -f "${KERNEL_SRC_DIR}/aurorasu.fragment" ]]; then
        cp "${KERNEL_SRC_DIR}/aurorasu.fragment" \
           "${KERNEL_SRC_DIR}/common/arch/arm64/configs/aurorasu.fragment"
        bazel_fragment_args="--defconfig_fragment=//common:arch/arm64/configs/aurorasu.fragment"
        log_info "使用 KSU defconfig fragment"
    fi

    # 清理 ABI protected exports (防止 WiFi/Bluetooth 错误)
    if [[ -d "${KERNEL_SRC_DIR}/common/android" ]]; then
        log_info "清理 ABI protected exports..."
        rm -f "${KERNEL_SRC_DIR}/common/android/abi_gki_protected_exports_"*
        if [[ -f "${KERNEL_SRC_DIR}/common/BUILD.bazel" ]]; then
            perl -0pi -e 's/^\s*"protected_exports_list"\s*:\s*"android\/abi_gki_protected_exports_aarch64",\s*$//m' \
                "${KERNEL_SRC_DIR}/common/BUILD.bazel" 2>/dev/null || true
        fi
    fi

    # 执行 Bazel 构建
    log_info "开始 Bazel 内核编译..."
    log_info "  目标: //common:kernel_aarch64_dist"
    log_info "  线程: ${JOBS}"

    BUILD_START_TIME=$(date +%s)

    if [[ "${VERBOSE}" == "true" ]]; then
        bazel build \
            --config=fast \
            ${bazel_fragment_args} \
            //common:kernel_aarch64_dist \
            2>&1 | tee "${BUILD_LOG}"
    else
        bazel build \
            --config=fast \
            ${bazel_fragment_args} \
            //common:kernel_aarch64_dist \
            2>&1 | tee "${BUILD_LOG}"
    fi

    local build_result=$?
    local build_end_time=$(date +%s)
    local build_duration=$((build_end_time - BUILD_START_TIME))

    if [[ ${build_result} -eq 0 ]]; then
        log_info "Bazel 编译成功！耗时: ${build_duration}s"
    else
        log_error "Bazel 编译失败 (退出码: ${build_result})"
        log_error "查看日志: ${BUILD_LOG}"
        exit ${build_result}
    fi
}

# ============================================================================
# 使用 build/build.sh 构建
# ============================================================================
build_with_build_sh() {
    log_section "build/build.sh 构建"

    cd "${KERNEL_SRC_DIR}"

    local fragment_args=""
    if [[ -f "${KERNEL_SRC_DIR}/aurorasu.fragment" ]]; then
        fragment_args="GKI_DEFCONFIG_FRAGMENT=${KERNEL_SRC_DIR}/aurorasu.fragment"
        log_info "使用 KSU defconfig fragment"
    fi

    BUILD_START_TIME=$(date +%s)

    log_info "开始编译..."
    log_info "  BUILD_CONFIG=common/build.config.gki.aarch64"
    log_info "  LTO=${LTO}"

    # 设置环境变量
    export PATH="${CLANG_DIR}/bin:${GCC_DIR}/bin:${PATH}"
    export CROSS_COMPILE="${CLANG_DIR}/bin/${CROSS_COMPILE}"
    export CLANG_TRIPLE="${CLANG_TRIPLE}"
    export LD="${CLANG_DIR}/bin/ld.lld"
    export AR="${CLANG_DIR}/bin/llvm-ar"
    export NM="${CLANG_DIR}/bin/llvm-nm"
    export OBJCOPY="${CLANG_DIR}/bin/llvm-objcopy"

    SKIP_MRPROPER=1 \
        LTO=${LTO} \
        ${fragment_args} \
        BUILD_CONFIG=common/build.config.gki.aarch64 \
        build/build.sh \
        -j${JOBS} \
        2>&1 | tee "${BUILD_LOG}"

    local build_result=$?
    local build_end_time=$(date +%s)
    local build_duration=$((build_end_time - BUILD_START_TIME))

    if [[ ${build_result} -eq 0 ]]; then
        log_info "编译成功！耗时: ${build_duration}s"
    else
        log_error "编译失败 (退出码: ${build_result})"
        log_error "查看日志: ${BUILD_LOG}"
        exit ${build_result}
    fi
}

# ============================================================================
# 使用传统 Makefile 构建
# ============================================================================
build_with_make() {
    log_section "Makefile 构建"

    cd "${KERNEL_SRC_DIR}"

    # 设置环境变量
    export PATH="${CLANG_DIR}/bin:${GCC_DIR}/bin:${PATH}"

    if [[ "${CLANG_ONLY}" == "true" ]]; then
        export CROSS_COMPILE="${CLANG_DIR}/bin/${CROSS_COMPILE}"
        export CLANG_TRIPLE="${CLANG_TRIPLE}"
    else
        export CROSS_COMPILE="${GCC_DIR}/bin/${CROSS_COMPILE}"
    fi

    export LD="${CLANG_DIR}/bin/ld.lld"
    export AR="${CLANG_DIR}/bin/llvm-ar"
    export NM="${CLANG_DIR}/bin/llvm-nm"
    export OBJCOPY="${CLANG_DIR}/bin/llvm-objcopy"
    export CC="${CLANG_DIR}/bin/clang"

    # 确定 O= 输出目录
    local o_flag="O=${OUT_DIR}"

    # 查找 defconfig
    if [[ ! -f "arch/${ARCH}/configs/${DEFCONFIG}" ]]; then
        log_warn "defconfig ${DEFCONFIG} 不在标准位置，搜索中..."
        local found=false
        for dir in \
            "common/arch/${ARCH}/configs" \
            "private/pineapple/arch/${ARCH}/configs" \
            "private/pineapple-msm/arch/${ARCH}/configs"; do
            if [[ -f "${dir}/${DEFCONFIG}" ]]; then
                DEFCONFIG_PATH="${dir}/${DEFCONFIG}"
                found=true
                break
            fi
        done
        if [[ "${found}" == "false" ]]; then
            log_error "未找到 defconfig: ${DEFCONFIG}"
            log_error "可用的 defconfig:"
            find . -path "*/arch/${ARCH}/configs/*_defconfig" 2>/dev/null | head -20
            exit 1
        fi
    else
        DEFCONFIG_PATH="arch/${ARCH}/configs/${DEFCONFIG}"
    fi

    # 生成 defconfig
    log_info "生成 defconfig: ${DEFCONFIG_PATH}"
    make ${o_flag} ARCH="${ARCH}" "${DEFCONFIG}"

    # 如果有 KSU fragment，合并配置
    if [[ -f "${KERNEL_SRC_DIR}/aurorasu.fragment" ]]; then
        log_info "合并 KSU 配置 fragment..."
        scripts/kconfig/merge_config.sh \
            "${OUT_DIR}/.config" \
            "${KERNEL_SRC_DIR}/aurorasu.fragment" \
            2>/dev/null || true
        make ${o_flag} ARCH="${ARCH}" olddefconfig
    fi

    # 编译内核
    BUILD_START_TIME=$(date +%s)

    log_info "开始 Makefile 编译..."
    log_info "  ARCH=${ARCH}"
    log_info "  CROSS_COMPILE=${CROSS_COMPILE}"
    log_info "  CC=${CC}"
    log_info "  JOBS=${JOBS}"

    make ${o_flag} \
        ARCH="${ARCH}" \
        -j"${JOBS}" \
        2>&1 | tee "${BUILD_LOG}"

    local build_result=$?
    local build_end_time=$(date +%s)
    local build_duration=$((build_end_time - BUILD_START_TIME))

    if [[ ${build_result} -eq 0 ]]; then
        log_info "Makefile 编译成功！耗时: ${build_duration}s"
    else
        log_error "Makefile 编译失败 (退出码: ${build_result})"
        log_error "查看日志: ${BUILD_LOG}"
        exit ${build_result}
    fi
}

# ============================================================================
# 收集编译产物
# ============================================================================
collect_artifacts() {
    log_section "收集编译产物"

    local image_found=false
    local image_path=""

    # 搜索编译产物 (Image)
    local search_dirs=(
        "${KERNEL_SRC_DIR}/out/android14-6.1/dist"
        "${KERNEL_SRC_DIR}/out/android14-6.1-lts/dist"
        "${KERNEL_SRC_DIR}/out/dist"
        "${KERNEL_SRC_DIR}/bazel-bin/common/kernel_aarch64"
        "${OUT_DIR}/arch/${ARCH}/boot"
    )

    for dir in "${search_dirs[@]}"; do
        if [[ -f "${dir}/Image" ]]; then
            image_path="${dir}/Image"
            image_found=true
            break
        fi
    done

    if [[ "${image_found}" == "true" ]]; then
        log_info "找到内核 Image: ${image_path}"

        # 复制到输出目录
        cp "${image_path}" "${DIST_DIR}/Image"
        log_info "已复制到: ${DIST_DIR}/Image"

        # 复制其他产物
        local artifact_dir
        artifact_dir="$(dirname "${image_path}")"

        for artifact in Image.gz Image.lz4 dtb.img dtbo.img; do
            if [[ -f "${artifact_dir}/${artifact}" ]]; then
                cp "${artifact_dir}/${artifact}" "${DIST_DIR}/"
                log_info "已复制: ${artifact}"
            fi
        done

        # 显示 Image 信息
        if [[ -f "${DIST_DIR}/Image" ]]; then
            local img_size
            img_size=$(stat -c%s "${DIST_DIR}/Image")
            log_info "Image 大小: $((img_size / 1024 / 1024))MB"
        fi
    else
        log_warn "未找到编译产物 Image"
        log_warn "请检查以下目录:"
        for dir in "${search_dirs[@]}"; do
            log_warn "  ${dir}"
        done
    fi

    # 显示产物列表
    echo ""
    log_info "输出目录内容:"
    ls -lh "${DIST_DIR}/" 2>/dev/null || echo "  (空)"
}

# ============================================================================
# 生成 defconfig
# ============================================================================
do_defconfig() {
    log_section "生成 defconfig"

    cd "${KERNEL_SRC_DIR}"

    if [[ "${BUILD_SYSTEM}" == "makefile" ]] || [[ "${USE_MAKE}" == "true" ]]; then
        make ARCH="${ARCH}" "${DEFCONFIG}"
        log_info "defconfig 已生成到: .config"
    else
        log_info "Bazel 构建系统将在编译时自动生成 defconfig"
    fi
}

# ============================================================================
# 交互式配置
# ============================================================================
do_menuconfig() {
    log_section "交互式内核配置 (menuconfig)"

    cd "${KERNEL_SRC_DIR}"

    export PATH="${CLANG_DIR}/bin:${GCC_DIR}/bin:${PATH}"
    export ARCH="${ARCH}"

    if [[ "${CLANG_ONLY}" == "true" ]]; then
        export CROSS_COMPILE="${CLANG_DIR}/bin/${CROSS_COMPILE}"
    else
        export CROSS_COMPILE="${GCC_DIR}/bin/${CROSS_COMPILE}"
    fi

    # 先生成 defconfig
    make "${DEFCONFIG}"

    # 启动 menuconfig
    make menuconfig

    # 保存配置
    log_info "配置已保存到 .config"
    log_info "如需保存为新的 defconfig:"
    log_info "  make savedefconfig"
    log_info "  cp defconfig arch/${ARCH}/configs/my_defconfig"
}

# ============================================================================
# 主流程
# ============================================================================
main() {
    echo ""
    echo "============================================"
    echo "  OnePlus ACE5 内核编译"
    echo "  SoC: SM8650 (pineapple)"
    echo "  内核: 6.1 (Android 14 GKI)"
    echo "  架构: ARM64"
    echo "============================================"
    echo ""

    # 前置检查
    preflight_check

    # 清理操作
    if [[ "${DO_CLEAN}" == "true" ]]; then
        do_clean
        exit 0
    fi

    # 仅生成 defconfig
    if [[ "${DO_DEFCONFIG}" == "true" ]]; then
        do_defconfig
        exit 0
    fi

    # 交互式配置
    if [[ "${DO_MENUCONFIG}" == "true" ]]; then
        do_menuconfig
        exit 0
    fi

    # 集成 KSU
    if [[ "${WITH_KSU}" == "true" ]]; then
        setup_kernelsu
    fi

    # 根据构建系统选择编译方式
    if [[ "${USE_MAKE}" == "true" ]] || [[ "${BUILD_SYSTEM}" == "makefile" ]]; then
        build_with_make
    elif [[ "${BUILD_SYSTEM}" == "build_sh" ]]; then
        build_with_build_sh
    else
        build_with_bazel
    fi

    # 收集产物
    collect_artifacts

    # 编译摘要
    local build_end_time=$(date +%s)
    if [[ -n "${BUILD_START_TIME}" ]]; then
        local total_duration=$((build_end_time - BUILD_START_TIME))
        echo ""
        echo "============================================"
        echo "  编译完成！"
        echo "  总耗时: ${total_duration}s ($((total_duration / 60))m)"
        echo "============================================"
        echo ""
        echo "  产物目录: ${DIST_DIR}"
        echo "  编译日志: ${BUILD_LOG}"
        echo ""
    fi
}

main "$@"
