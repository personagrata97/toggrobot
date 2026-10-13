#!/bin/bash
# Emulatorde uygulamayi acar ve her ekranin goruntusunu alir.
set -x
mkdir -p shots
P=com.toggrobot
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell pm grant $P android.permission.POST_NOTIFICATIONS
adb shell dumpsys deviceidle whitelist +$P
shot() { sleep "${2:-3}"; adb exec-out screencap -p > "shots/$1.png"; }
scroll() { adb shell input swipe 540 1900 540 700 400; }

adb shell am start -W -n $P/.MainActivity
shot 01-ana 15
adb shell am force-stop $P
adb shell am start -W -n $P/.MainActivity
shot 02-ana-veriyle 6
scroll; shot 03-ana-asagi 2

adb shell am start -W -n $P/.RobotEditActivity --es id r1
shot 04-robot 6
scroll; shot 05-robot-2 2
scroll; shot 06-robot-3 2
scroll; shot 07-robot-4 2

adb shell am start -W -n $P/.SiteActivity
shot 08-site 4
scroll; shot 09-site-2 2

adb shell am start -W -n $P/.SettingsActivity
shot 10-ayarlar 3
scroll; shot 11-ayarlar-2 2

adb shell am start -W -n $P/.RobotEditActivity
shot 12-yeni-robot 6

adb logcat -d -s AndroidRuntime:E > shots/crash-log.txt
ls -la shots
