# phantom_module Makefile
# External kernel module for OnePlus ACE5 (SM8650) - Android GKI 6.1 ARM64
#
# Usage:
#   make -C kernel_module KDIR=/path/to/kernel/headers ARCH=arm64 CROSS_COMPILE=<toolchain-prefix> CC=clang
#
# Example (with AOSP Clang):
#   make -C kernel_module \
#     KDIR=/path/to/gki-kernel-headers \
#     ARCH=arm64 \
#     CROSS_COMPILE=/path/to/clang/bin/aarch64-linux-gnu- \
#     CC=/path/to/clang/bin/clang \
#     CLANG_TRIPLE=aarch64-linux-gnu-

obj-m += phantom_module.o

# Allow overriding KDIR from command line
KDIR ?= /lib/modules/$(shell uname -r)/build

# Default cross-compilation settings (can be overridden)
ARCH ?= arm64
CROSS_COMPILE ?= aarch64-linux-gnu-

# Clang/LLVM settings for Android kernel builds
ifneq ($(origin CC),command line)
  CC ?= $(CROSS_COMPILE)gcc
endif

# Extra compiler flags for GKI compatibility
ccflags-y += -Wno-strict-prototypes \
             -Wno-int-conversion \
             -Wno-unused-function \
             -Wno-unused-variable \
             -Wno-declaration-after-statement

# Module name output
TARGET := phantom_module.ko

.PHONY: all clean modules install

all: modules

modules:
	$(MAKE) -C $(KDIR) M=$(CURDIR) \
		ARCH=$(ARCH) \
		CROSS_COMPILE=$(CROSS_COMPILE) \
		CC=$(CC) \
		$(if $(CLANG_TRIPLE),CLANG_TRIPLE=$(CLANG_TRIPLE)) \
		$(if $(LD),LD=$(LD)) \
		$(if $(AR),AR=$(AR)) \
		$(if $(NM),NM=$(NM)) \
		$(if $(OBJCOPY),OBJCOPY=$(OBJCOPY)) \
		$(if $(OBJDUMP),OBJDUMP=$(OBJDUMP)) \
		$(if $(READELF),READELF=$(READELF)) \
		modules

clean:
	$(MAKE) -C $(KDIR) M=$(CURDIR) clean
	rm -f *.o *.ko *.mod *.mod.c *.mod.o *.order *.symvers *.cmd .*.cmd
	rm -rf .tmp_versions Module.symvers modules.order

install: modules
	$(MAKE) -C $(KDIR) M=$(CURDIR) \
		ARCH=$(ARCH) \
		CROSS_COMPILE=$(CROSS_COMPILE) \
		CC=$(CC) \
		$(if $(CLANG_TRIPLE),CLANG_TRIPLE=$(CLANG_TRIPLE)) \
		modules_install
