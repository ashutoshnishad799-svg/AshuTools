# Ashutool - root toolkit for custom ROMs
# Copyright (C) 2026 Ashutosh Nishad
# Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
#
# Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
# Keep this header and the author credit in every copy and modified version.

# Ashutool release rules.
# Keep line numbers so a crash report from the About page can still be read.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# libsu runs the root shell
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# Started as root with app_process to switch 5G and 4G, so its entry point must keep its name
-keep class com.ashu.ashutool.NetModeMain { public static void main(java.lang.String[]); }

