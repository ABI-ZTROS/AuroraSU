# AuroraSU Kernel Module Makefile

obj-m := aurora.o
aurora-objs := core/init.o

KDIR ?= /lib/modules/$(shell uname -r)/build
PWD := $(shell pwd)

# Compiler flags
ccflags-y := -I$(PWD)/include -DAURORA_VERSION_MAJOR=1 -DAURORA_VERSION_MINOR=0 -DAURORA_VERSION_PATCH=0

# Feature flags (can be overridden)
CONFIG_AURORA_DEBUG ?= n
CONFIG_AURORA_KPM ?= y
CONFIG_AURORA_REDUNDANCY ?= y

ifeq ($(CONFIG_AURORA_DEBUG),y)
    ccflags-y += -DCONFIG_AURORA_DEBUG
endif

ifeq ($(CONFIG_AURORA_KPM),y)
    ccflags-y += -DCONFIG_AURORA_KPM
endif

ifeq ($(CONFIG_AURORA_REDUNDANCY),y)
    ccflags-y += -DCONFIG_AURORA_REDUNDANCY
endif

# Architecture detection
ARCH ?= $(shell uname -m)
ifeq ($(ARCH),aarch64)
    ARCH := arm64
endif

all:
	$(MAKE) -C $(KDIR) M=$(PWD) modules

clean:
	$(MAKE) -C $(KDIR) M=$(PWD) clean

install:
	$(MAKE) -C $(KDIR) M=$(PWD) modules_install
	depmod -a

# Create a minimal module for demo
minimal:
	@echo "Creating minimal AuroraSU module..."
	$(MAKE) CONFIG_AURORA_KPM=n CONFIG_AURORA_REDUNDANCY=n all

.PHONY: all clean install minimal
