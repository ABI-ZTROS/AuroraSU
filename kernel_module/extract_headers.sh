#!/usr/bin/env bash
# ============================================================================
# extract_headers.sh - GKI 6.1 内核头文件提取脚本
# ============================================================================
# 用途: 从已编译的 OnePlus ACE5 内核源码中提取 GKI 内核头文件，
#       供内核模块 (如 AuroraSU/KSU) 的 out-of-tree 编译使用。
#
# 提取内容:
#   - include/           内核主要头文件
#   - arch/arm64/include/ ARM64 架构头文件
#   - scripts/           编译辅助脚本 (genksyms, modpost 等)
#   - Module.symvers     内核符号版本表
#   - .config            内核编译配置
#   - Kconfig            Kconfig 根文件
#   - Makefile           Makefile 根文件
#
# 前置条件:
#   1. 内核源码已通过 build_env.sh 拉取
#   2. 内核已通过 build_kernel.sh 编译 (至少完成过一次 make modules_prepare)
#
# 用法:
#   ./extract_headers.sh              # 提取到默认目录
#   ./extract_headers.sh --outdir=DIR # 指定输出目录
#   ./extract_headers.sh --prepare    # 仅执行 modules_prepare
#   ./extract_headers.sh --clean      # 清理已提取的头文件
#   ./extract_headers.sh --help       # 显示帮助
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
# 默认配置
# ============================================================================
# 输出目录 (可通过命令行覆盖)
HEADER_OUT_DIR="${GKI_HEADERS_DIR:-${KERNEL_MODULE_ROOT}/gki_headers}"

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
log_section() { echo -e "\n${BLUE}==== $* ====${NC}\n"; }

# ============================================================================
# 显示帮助
# ============================================================================
show_help() {
    cat << EOF
用法: ./extract_headers.sh [选项]

选项:
  --outdir=DIR    指定头文件输出目录 (默认: ${HEADER_OUT_DIR})
  --prepare       仅执行 modules_prepare (不提取头文件)
  --clean         清理已提取的头文件目录
  --help, -h      显示此帮助信息

说明:
  此脚本从已编译的内核源码中提取 GKI 头文件，供内核模块
  (out-of-tree 模块) 编译使用。

  提取的头文件包含:
    - include/              内核 API 头文件
    - arch/arm64/include/   ARM64 架构特定头文件
    - scripts/              编译辅助工具
    - Module.symvers        内核符号版本表
    - .config               内核编译配置
    - Makefile, Kconfig     构建系统文件

  模块编译时使用:
    make -C /path/to/gki_headers M=/path/to/module

示例:
  ./extract_headers.sh
  ./extract_headers.sh --outdir=/opt/gki-headers
  ./extract_headers.sh --prepare
EOF
    exit 0
}

# ============================================================================
# 解析命令行参数
# ============================================================================
DO_PREPARE=false
DO_CLEAN=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --outdir=*)    HEADER_OUT_DIR="${1#*=}" ;;
        --prepare)     DO_PREPARE=true ;;
        --clean)       DO_CLEAN=true ;;
        --help|-h)     show_help ;;
        *)
            log_error "未知参数: $1"
            show_help
            ;;
    esac
    shift
done

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

    # 检查是否已编译过内核
    local config_found=false
    for config_path in \
        "${KERNEL_SRC_DIR}/.config" \
        "${KERNEL_SRC_DIR}/out/android14-6.1/.config" \
        "${KERNEL_SRC_DIR}/out/android14-6.1-lts/.config" \
        "${KERNEL_SRC_DIR}/out/.config"; do
        if [[ -f "${config_path}" ]]; then
            log_info "找到内核配置: ${config_path}"
            config_found=true
            break
        fi
    done

    if [[ "${config_found}" == "false" ]]; then
        log_warn "未找到已编译的内核配置 (.config)"
        log_warn "将尝试执行 modules_prepare..."
    fi

    log_info "前置检查通过"
}

# ============================================================================
# 执行 modules_prepare (生成编译模块所需的文件)
# ============================================================================
do_modules_prepare() {
    log_section "执行 modules_prepare"

    cd "${KERNEL_SRC_DIR}"

    # 设置环境变量
    export PATH="${CLANG_DIR}/bin:${GCC_DIR}/bin:${PATH}"
    export ARCH="${ARCH}"
    export CROSS_COMPILE="${CLANG_DIR}/bin/${CROSS_COMPILE}"
    export CLANG_TRIPLE="${CLANG_TRIPLE}"
    export CC="${CLANG_DIR}/bin/clang"
    export LD="${CLANG_DIR}/bin/ld.lld"
    export AR="${CLANG_DIR}/bin/llvm-ar"
    export NM="${CLANG_DIR}/bin/llvm-nm"
    export OBJCOPY="${CLANG_DIR}/bin/llvm-objcopy"

    # 查找 .config 位置
    local config_dir="."
    for dir in \
        "out/android14-6.1" \
        "out/android14-6.1-lts" \
        "out"; do
        if [[ -f "${dir}/.config" ]]; then
            config_dir="${dir}"
            break
        fi
    done

    # 如果没有 .config，先生成 defconfig
    if [[ ! -f "${config_dir}/.config" ]]; then
        log_info "生成 defconfig..."

        # 查找可用的 defconfig
        local defconfig=""
        for cfg in \
            "pineapple_defconfig" \
            "gki_defconfig" \
            "common/arch/${ARCH}/configs/pineapple_defconfig" \
            "arch/${ARCH}/configs/gki_defconfig"; do
            if [[ -f "${cfg}" ]]; then
                defconfig="${cfg}"
                break
            fi
        done

        if [[ -n "${defconfig}" ]]; then
            make ARCH="${ARCH}" "${defconfig}"
        else
            log_error "未找到可用的 defconfig"
            exit 1
        fi
    fi

    # 执行 modules_prepare
    log_info "执行 make modules_prepare..."
    make ARCH="${ARCH}" modules_prepare -j"$(nproc --all)"

    log_info "modules_prepare 完成"
}

# ============================================================================
# 提取头文件
# ============================================================================
extract_headers() {
    log_section "提取 GKI 内核头文件"

    cd "${KERNEL_SRC_DIR}"

    # 确定内核构建输出目录
    local kernel_build_dir=""
    for dir in \
        "out/android14-6.1" \
        "out/android14-6.1-lts" \
        "out" \
        "."; do
        if [[ -f "${dir}/.config" ]]; then
            kernel_build_dir="${dir}"
            break
        fi
    done

    if [[ -z "${kernel_build_dir}" ]]; then
        log_error "未找到内核构建目录"
        exit 1
    fi

    log_info "内核构建目录: ${kernel_build_dir}"

    # 创建输出目录结构
    mkdir -p "${HEADER_OUT_DIR}"

    # ---- 1. 复制 include/ 目录 ----
    log_info "[1/8] 复制 include/ 头文件..."
    mkdir -p "${HEADER_OUT_DIR}/include"
    cp -a "${KERNEL_SRC_DIR}/include/"* "${HEADER_OUT_DIR}/include/"

    # ---- 2. 复制 arch/arm64/include/ ----
    log_info "[2/8] 复制 arch/arm64/include/ 头文件..."
    mkdir -p "${HEADER_OUT_DIR}/arch/arm64/include"
    cp -a "${KERNEL_SRC_DIR}/arch/arm64/include/"* \
        "${HEADER_OUT_DIR}/arch/arm64/include/"

    # ---- 3. 复制 arch/arm64/lib/ (模块链接需要) ----
    log_info "[3/8] 复制 arch/arm64/lib/..."
    mkdir -p "${HEADER_OUT_DIR}/arch/arm64/lib"
    if [[ -d "${KERNEL_SRC_DIR}/arch/arm64/lib" ]]; then
        cp -a "${KERNEL_SRC_DIR}/arch/arm64/lib/"* \
            "${HEADER_OUT_DIR}/arch/arm64/lib/" 2>/dev/null || true
    fi

    # ---- 4. 复制 scripts/ (编译辅助工具) ----
    log_info "[4/8] 复制 scripts/ 编译辅助工具..."
    mkdir -p "${HEADER_OUT_DIR}/scripts"
    # 只复制模块编译需要的脚本
    for script_dir in \
        "scripts/basic" \
        "scripts/dtc" \
        "scripts/genksyms" \
        "scripts/mod" \
        "scripts/module" \
        "scripts/tools" \
        "scripts/Kbuild.include" \
        "scripts/Makefile" \
        "scripts/Makefile.build" \
        "scripts/Makefile.clean" \
        "scripts/Makefile.lib" \
        "scripts/Makefile.modinst" \
        "scripts/Makefile.modpost" \
        "scripts/Makefile.headersinst" \
        "scripts/modpost" \
        "scripts/sign-file"; do
        if [[ -e "${KERNEL_SRC_DIR}/${script_dir}" ]]; then
            local target_dir="${HEADER_OUT_DIR}/$(dirname "${script_dir}")"
            mkdir -p "${target_dir}"
            cp -a "${KERNEL_SRC_DIR}/${script_dir}" "${target_dir}/"
        fi
    done

    # ---- 5. 复制 Module.symvers ----
    log_info "[5/8] 复制 Module.symvers..."
    if [[ -f "${kernel_build_dir}/Module.symvers" ]]; then
        cp "${kernel_build_dir}/Module.symvers" "${HEADER_OUT_DIR}/"
        log_info "  Module.symvers: $(wc -l < "${HEADER_OUT_DIR}/Module.symvers") 个符号"
    elif [[ -f "${KERNEL_SRC_DIR}/Module.symvers" ]]; then
        cp "${KERNEL_SRC_DIR}/Module.symvers" "${HEADER_OUT_DIR}/"
        log_info "  Module.symvers: $(wc -l < "${HEADER_OUT_DIR}/Module.symvers") 个符号"
    else
        log_warn "  Module.symvers 未找到 (可能尚未编译模块)"
        # 创建空文件
        touch "${HEADER_OUT_DIR}/Module.symvers"
    fi

    # ---- 6. 复制内核配置 ----
    log_info "[6/8] 复制内核配置..."
    cp "${kernel_build_dir}/.config" "${HEADER_OUT_DIR}/.config"
    # 生成 autoconf.h 等自动生成的头文件
    mkdir -p "${HEADER_OUT_DIR}/include/generated"
    if [[ -d "${kernel_build_dir}/include/generated" ]]; then
        cp -a "${kernel_build_dir}/include/generated/"* \
            "${HEADER_OUT_DIR}/include/generated/" 2>/dev/null || true
    fi

    # ---- 7. 复制构建系统文件 ----
    log_info "[7/8] 复制构建系统文件..."
    cp "${KERNEL_SRC_DIR}/Makefile" "${HEADER_OUT_DIR}/"
    cp "${KERNEL_SRC_DIR}/Kconfig" "${HEADER_OUT_DIR}/"
    if [[ -f "${KERNEL_SRC_DIR}/arch/arm64/Kconfig" ]]; then
        mkdir -p "${HEADER_OUT_DIR}/arch/arm64"
        cp "${KERNEL_SRC_DIR}/arch/arm64/Kconfig" "${HEADER_OUT_DIR}/arch/arm64/"
    fi

    # ---- 8. 创建模块编译包装脚本 ----
    log_info "[8/8] 创建模块编译辅助文件..."

    # 创建 Kbuild 文件 (用于 out-of-tree 模块编译)
    cat > "${HEADER_OUT_DIR}/Kbuild" << 'EOF'
# Kbuild for out-of-tree module compilation
include Makefile
EOF

    # 创建 Module.makefile 模板
    cat > "${HEADER_OUT_DIR}/Module.makefile" << 'MAKEEOF'
# ============================================================================
# 内核模块编译模板
# 用法: make -C /path/to/gki_headers M=$(pwd) modules
# ============================================================================

# 模块名称
MODULE_NAME ?= my_module

# 模块源文件
obj-m += $(MODULE_NAME).o

# 如果模块有多个源文件:
# $(MODULE_NAME)-objs := file1.o file2.o

# 编译标志
# ccflags-y += -I$(src)/include
# ccflags-y += -DDEBUG

# Clang/LLVM 配置 (取消注释以使用 Clang)
# CC := /path/to/clang/bin/clang
# LD := /path/to/clang/bin/ld.lld
# AR := /path/to/clang/bin/llvm-ar
# NM := /path/to/clang/bin/llvm-nm

MAKEEOF

    log_info "头文件提取完成"
}

# ============================================================================
# 生成模块编译环境脚本
# ============================================================================
generate_module_env() {
    log_section "生成模块编译环境脚本"

    local module_env_file="${HEADER_OUT_DIR}/module_env.sh"

    cat > "${module_env_file}" << ENVEOF
#!/usr/bin/env bash
# ============================================================================
# module_env.sh - 内核模块编译环境变量
# 由 extract_headers.sh 生成
# 用法: source /path/to/gki_headers/module_env.sh
# ============================================================================

# 内核头文件目录 (即此脚本所在目录)
export KROOT="\$(cd "\$(dirname "\${BASH_SOURCE[0]}")" && pwd)"

# 架构
export ARCH="${ARCH}"
export SUBARCH="arm64"

# 交叉编译配置
export CROSS_COMPILE="${CLANG_DIR}/bin/${CROSS_COMPILE}"
export CLANG_TRIPLE="${CLANG_TRIPLE}"

# Clang/LLVM 工具链
export CC="${CLANG_DIR}/bin/clang"
export LD="${CLANG_DIR}/bin/ld.lld"
export AR="${CLANG_DIR}/bin/llvm-ar"
export NM="${CLANG_DIR}/bin/llvm-nm"
export OBJCOPY="${CLANG_DIR}/bin/llvm-objcopy"
export OBJDUMP="${CLANG_DIR}/bin/llvm-objdump"
export STRIP="${CLANG_DIR}/bin/llvm-strip"

# PATH
export PATH="${CLANG_DIR}/bin:\${PATH}"

# 编译标志
export KCFLAGS="-O2 -Wno-error"
export KBUILD_MODPOST_WARN=1

echo "[MODULE ENV] 内核模块编译环境已加载"
echo "[MODULE ENV] KROOT=\${KROOT}"
echo "[MODULE ENV] ARCH=\${ARCH}"
echo "[MODULE ENV] CC=\${CC}"
echo ""
echo "编译模块示例:"
echo "  make -C \${KROOT} M=/path/to/module modules"
echo ""
echo "安装模块示例:"
echo "  make -C \${KROOT} M=/path/to/module modules_install INSTALL_MOD_PATH=/path/to/out"
echo ""
echo "清理模块:"
echo "  make -C \${KROOT} M=/path/to/module clean"
ENVEOF

    chmod +x "${module_env_file}"
    log_info "模块编译环境脚本: ${module_env_file}"
}

# ============================================================================
# 清理已提取的头文件
# ============================================================================
do_clean() {
    log_section "清理 GKI 头文件"

    if [[ -d "${HEADER_OUT_DIR}" ]]; then
        log_info "删除目录: ${HEADER_OUT_DIR}"
        rm -rf "${HEADER_OUT_DIR}"
        log_info "清理完成"
    else
        log_info "目录不存在: ${HEADER_OUT_DIR}"
    fi
}

# ============================================================================
# 验证提取结果
# ============================================================================
verify_extraction() {
    log_section "验证提取结果"

    local errors=0

    # 检查关键文件
    local required_files=(
        "include/linux/kernel.h"
        "include/linux/module.h"
        "include/linux/init.h"
        "include/uapi/linux/types.h"
        "arch/arm64/include/asm/unistd.h"
        "scripts/Makefile.modpost"
        "Makefile"
        "Kconfig"
        ".config"
    )

    for file in "${required_files[@]}"; do
        if [[ -f "${HEADER_OUT_DIR}/${file}" ]]; then
            log_info "  [OK] ${file}"
        else
            log_warn "  [MISSING] ${file}"
            ((errors++))
        fi
    done

    # 检查 Module.symvers
    if [[ -f "${HEADER_OUT_DIR}/Module.symvers" ]]; then
        local sym_count
        sym_count=$(wc -l < "${HEADER_OUT_DIR}/Module.symvers")
        log_info "  Module.symvers: ${sym_count} 个符号"
    else
        log_warn "  Module.symvers: 空 (编译模块时可能缺少符号)"
    fi

    # 统计头文件数量
    local header_count
    header_count=$(find "${HEADER_OUT_DIR}/include" -name "*.h" 2>/dev/null | wc -l)
    log_info "  头文件总数: ${header_count}"

    # 计算总大小
    local total_size
    total_size=$(du -sh "${HEADER_OUT_DIR}" 2>/dev/null | cut -f1)
    log_info "  总大小: ${total_size}"

    if [[ ${errors} -gt 0 ]]; then
        log_warn "验证完成，${errors} 个文件缺失"
    else
        log_info "验证通过，所有关键文件存在"
    fi
}

# ============================================================================
# 主流程
# ============================================================================
main() {
    echo ""
    echo "============================================"
    echo "  GKI 6.1 内核头文件提取"
    echo "  目标: OnePlus ACE5 (SM8650/pineapple)"
    echo "  架构: ARM64"
    echo "============================================"
    echo ""

    # 清理操作
    if [[ "${DO_CLEAN}" == "true" ]]; then
        do_clean
        exit 0
    fi

    # 前置检查
    preflight_check

    # 执行 modules_prepare
    if [[ "${DO_PREPARE}" == "true" ]]; then
        do_modules_prepare
        exit 0
    fi

    # 执行 modules_prepare + 提取头文件
    do_modules_prepare
    extract_headers
    generate_module_env
    verify_extraction

    # 最终摘要
    echo ""
    echo "============================================"
    echo "  头文件提取完成！"
    echo "============================================"
    echo ""
    echo "  输出目录: ${HEADER_OUT_DIR}"
    echo ""
    echo "  使用方法:"
    echo ""
    echo "  1. 加载模块编译环境:"
    echo "     source ${HEADER_OUT_DIR}/module_env.sh"
    echo ""
    echo "  2. 编译内核模块:"
    echo "     make -C ${HEADER_OUT_DIR} M=/path/to/module modules"
    echo ""
    echo "  3. 或使用 Makefile 模板:"
    echo "     cp ${HEADER_OUT_DIR}/Module.makefile /path/to/module/Makefile"
    echo ""
    echo "  AuroraSU 模块编译示例:"
    echo "     source ${HEADER_OUT_DIR}/module_env.sh"
    echo "     make -C ${HEADER_OUT_DIR} M=${KERNEL_MODULE_ROOT}/../kernel modules"
    echo ""
}

main "$@"
