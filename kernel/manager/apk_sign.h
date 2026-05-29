#ifndef __KSU_H_APK_V2_SIGN
#define __KSU_H_APK_V2_SIGN

#include <linux/types.h>

/* Default expected certificate size and hash */
#ifndef EXPECTED_SIZE
#define EXPECTED_SIZE 0
#define EXPECTED_HASH ""
#endif

bool is_manager_apk(char *path);
int get_pkg_from_apk_path(char *pkg, const char *path);

#endif
