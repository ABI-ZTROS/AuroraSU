/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Copyright (C) 2025 AuroraSU. All Rights Reserved.
 *
 * KPM (Kernel Patch Module) implementation for AuroraSU
 * Based on SukiSU-Ultra implementation
 *
 * 适配KernelSU的KPM内核模块加载器兼容实现
 * 集成了 ELF 解析、内存布局、符号处理、重定位（支持 ARM64 重定位类型）
 * 并参照KernelPatch的标准KPM格式实现加载和控制
 */

#include <linux/kernel.h>
#include <linux/fs.h>
#include <linux/kernfs.h>
#include <linux/file.h>
#include <linux/vmalloc.h>
#include <linux/uaccess.h>
#include <linux/elf.h>
#include <linux/kallsyms.h>
#include <linux/version.h>
#include <linux/list.h>
#include <linux/spinlock.h>
#include <linux/rcupdate.h>
#include <asm/elf.h>
#include <linux/mm.h>
#include <linux/string.h>
#include <asm/cacheflush.h>
#include <linux/module.h>
#include <linux/set_memory.h>
#include <linux/export.h>
#include <linux/slab.h>
#include <asm/insn.h>
#include <linux/kprobes.h>
#include <linux/stacktrace.h>

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0) && defined(CONFIG_MODULES)
#include <linux/moduleloader.h>
#endif

#include "kpm.h"
#include "../klog.h"

#ifndef NO_OPTIMIZE
#if defined(__GNUC__) && !defined(__clang__)
#define NO_OPTIMIZE __attribute__((optimize("O0")))
#elif defined(__clang__)
#define NO_OPTIMIZE __attribute__((optnone))
#else
#define NO_OPTIMIZE
#endif
#endif

/*
 * KPM stub implementations
 *
 * KPM (Kernel Patch Module) loading requires ELF parsing, kernel memory
 * management, symbol resolution, and architecture-specific relocation support.
 * These stubs return -ENOSYS to clearly indicate that KPM is not supported.
 * The manager UI should handle -ENOSYS gracefully by showing a
 * "KPM not supported" message instead of crashing.
 */

noinline NO_OPTIMIZE void sukisu_kpm_load_module_path(const char *path,
                                                      const char *args,
                                                      void *ptr, int *result)
{
    pr_warn("kpm: KPM module loading is not supported in this build. "
            "path=%s\n", path);

    *result = -ENOSYS;
    __asm__ volatile("nop");
}
EXPORT_SYMBOL(sukisu_kpm_load_module_path);

noinline NO_OPTIMIZE void sukisu_kpm_unload_module(const char *name, void *ptr,
                                                   int *result)
{
    pr_warn("kpm: KPM module unloading is not supported in this build. "
            "name=%s\n", name);

    *result = -ENOSYS;
    __asm__ volatile("nop");
}
EXPORT_SYMBOL(sukisu_kpm_unload_module);

noinline NO_OPTIMIZE void sukisu_kpm_num(int *result)
{
    /* No KPM modules can be loaded in this build */
    *result = 0;
}
EXPORT_SYMBOL(sukisu_kpm_num);

noinline NO_OPTIMIZE void sukisu_kpm_info(const char *name, char *buf,
                                          int bufferSize, int *size)
{
    pr_warn("kpm: KPM info query is not supported in this build. "
            "name=%s\n", name);

    *size = 0;
}
EXPORT_SYMBOL(sukisu_kpm_info);

noinline NO_OPTIMIZE void sukisu_kpm_list(void *out, int bufferSize,
                                          int *result)
{
    /* No modules to list */
    *result = 0;
}
EXPORT_SYMBOL(sukisu_kpm_list);

noinline NO_OPTIMIZE void sukisu_kpm_control(const char *name, const char *args,
                                             long arg_len, int *result)
{
    pr_warn("kpm: KPM control is not supported in this build. "
            "name=%s\n", name);

    *result = -ENOSYS;
    __asm__ volatile("nop");
}
EXPORT_SYMBOL(sukisu_kpm_control);

noinline NO_OPTIMIZE void sukisu_kpm_version(char *buf, int bufferSize)
{
    if (buf && bufferSize > 0) {
        strscpy(buf, "AuroraSU (KPM not supported)", bufferSize);
    }
}
EXPORT_SYMBOL(sukisu_kpm_version);

/*
 * Main KPM handler function
 * Dispatches KPM control codes to appropriate stub functions
 */
noinline int sukisu_handle_kpm(unsigned long control_code, unsigned long arg1,
                               unsigned long arg2, unsigned long result_code)
{
    int res = -EINVAL;

    switch (control_code) {
    case SUKISU_KPM_LOAD: {
        char kernel_load_path[256];
        char kernel_args_buffer[256];

        if (arg1 == 0) {
            res = -EINVAL;
            goto exit;
        }

        if (!access_ok((void __user *)arg1, 255)) {
            goto invalid_arg;
        }

        if (strncpy_from_user((char *)&kernel_load_path,
                              (const char __user *)arg1, 255) < 0) {
            goto invalid_arg;
        }
        kernel_load_path[255] = '\0';

        if (arg2 != 0) {
            if (!access_ok((void __user *)arg2, 255)) {
                goto invalid_arg;
            }

            if (strncpy_from_user((char *)&kernel_args_buffer,
                                  (const char __user *)arg2, 255) < 0) {
                goto invalid_arg;
            }
            kernel_args_buffer[255] = '\0';
        } else {
            kernel_args_buffer[0] = '\0';
        }

        sukisu_kpm_load_module_path((const char *)&kernel_load_path,
                                    (const char *)&kernel_args_buffer, NULL,
                                    &res);
        break;
    }

    case SUKISU_KPM_UNLOAD: {
        char kernel_name_buffer[256];

        if (arg1 == 0) {
            res = -EINVAL;
            goto exit;
        }

        if (!access_ok((void __user *)arg1, sizeof(kernel_name_buffer))) {
            goto invalid_arg;
        }

        if (strncpy_from_user((char *)&kernel_name_buffer,
                              (const char __user *)arg1,
                              sizeof(kernel_name_buffer) - 1) < 0) {
            goto invalid_arg;
        }
        kernel_name_buffer[sizeof(kernel_name_buffer) - 1] = '\0';

        sukisu_kpm_unload_module((const char *)&kernel_name_buffer, NULL, &res);
        break;
    }

    case SUKISU_KPM_NUM:
        sukisu_kpm_num(&res);
        break;

    case SUKISU_KPM_INFO: {
        char kernel_name_buffer[256];
        char buf[256];
        int size = 0;

        if (arg1 == 0 || arg2 == 0) {
            res = -EINVAL;
            goto exit;
        }

        if (!access_ok((void __user *)arg1, sizeof(kernel_name_buffer))) {
            goto invalid_arg;
        }

        if (strncpy_from_user((char *)&kernel_name_buffer,
                              (const char __user *)arg1,
                              sizeof(kernel_name_buffer) - 1) < 0) {
            goto invalid_arg;
        }
        kernel_name_buffer[sizeof(kernel_name_buffer) - 1] = '\0';

        sukisu_kpm_info((const char *)&kernel_name_buffer, (char *)&buf,
                        sizeof(buf), &size);

        if (!access_ok((void __user *)arg2, size)) {
            goto invalid_arg;
        }

        res = copy_to_user((void __user *)arg2, &buf, size) ? -EFAULT : 0;
        break;
    }

    case SUKISU_KPM_LIST: {
        char buf[1024];
        int len = (int)arg2;

        if (len <= 0) {
            res = -EINVAL;
            goto exit;
        }

        if (!access_ok((void __user *)arg1, len)) {
            goto invalid_arg;
        }

        sukisu_kpm_list((char *)&buf, sizeof(buf), &res);

        if (res > len) {
            res = -ENOBUFS;
            goto exit;
        }

        if (copy_to_user((void __user *)arg1, &buf, res) != 0)
            pr_info("kpm: Copy to user failed.\n");

        break;
    }

    case SUKISU_KPM_CONTROL: {
        char kpm_name[KPM_NAME_LEN] = { 0 };
        char kpm_args[KPM_ARGS_LEN] = { 0 };

        if (!access_ok((void __user *)arg1, sizeof(kpm_name))) {
            goto invalid_arg;
        }

        if (!access_ok((void __user *)arg2, sizeof(kpm_args))) {
            goto invalid_arg;
        }

        long name_len = strncpy_from_user(
            (char *)&kpm_name, (const char __user *)arg1, sizeof(kpm_name) - 1);
        if (name_len < 0) {
            res = -EFAULT;
            goto exit;
        }
        kpm_name[sizeof(kpm_name) - 1] = '\0';

        long arg_len = strncpy_from_user(
            (char *)&kpm_args, (const char __user *)arg2, sizeof(kpm_args) - 1);
        if (arg_len < 0) {
            arg_len = 0;
        }
        kpm_args[sizeof(kpm_args) - 1] = '\0';

        sukisu_kpm_control((const char *)&kpm_name, (const char *)&kpm_args,
                           arg_len, &res);
        break;
    }

    case SUKISU_KPM_VERSION: {
        char buffer[256] = { 0 };

        sukisu_kpm_version((char *)&buffer, sizeof(buffer));

        unsigned int outlen = (unsigned int)arg2;
        int len = strlen(buffer);
        if (len >= (int)outlen)
            len = outlen - 1;

        res = copy_to_user((void __user *)arg1, &buffer, len + 1) ? -EFAULT : 0;
        break;
    }

    default:
        res = -ENOSYS;
        pr_warn("kpm: Unknown control code: %lu\n", control_code);
        break;
    }

exit:
    if (result_code != 0) {
        if (copy_to_user((void __user *)result_code, &res, sizeof(res)) != 0)
            pr_info("kpm: Copy result to user failed.\n");
    }

    return 0;

invalid_arg:
    pr_err("kpm: invalid pointer detected! arg1: %px arg2: %px\n", (void *)arg1,
           (void *)arg2);
    res = -EFAULT;
    goto exit;
}
EXPORT_SYMBOL(sukisu_handle_kpm);

/*
 * Check if the control code is a valid KPM command
 */
int sukisu_is_kpm_control_code(unsigned long control_code)
{
    return (control_code >= SUKISU_KPM_CONTROL_MIN &&
            control_code <= SUKISU_KPM_CONTROL_MAX) ? 1 : 0;
}
EXPORT_SYMBOL(sukisu_is_kpm_control_code);

/*
 * IOCTL handler for KPM operations
 */
int do_kpm(void __user *arg)
{
    struct ksu_kpm_cmd cmd;

    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        pr_err("kpm: copy_from_user failed\n");
        return -EFAULT;
    }

    if (!access_ok((void __user *)cmd.result_code, sizeof(int))) {
        pr_err("kpm: invalid result_code pointer %px\n",
               (void *)cmd.result_code);
        return -EFAULT;
    }

    return sukisu_handle_kpm(cmd.control_code, cmd.arg1, cmd.arg2,
                             cmd.result_code);
}
EXPORT_SYMBOL(do_kpm);
