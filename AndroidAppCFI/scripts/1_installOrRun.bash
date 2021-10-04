		#!/bin/bash

		current_dir=$(pwd)
		root_folder_path="${current_dir/"scripts"/""}"         
		echo "rootpath:${root_folder_path}"
        apk_location="/home/ishadi/Documents/AndroidCFI/app-code/weather/weather/app/"
		echo "apk location:$apk_location"
		
		if [[  "$1" = "1" ]]; then
			
			if [ ! -d "$apk_location"release ]; then
				echo "$apk_location doesn't exist!"
				exit -1
			fi
			
		
			mv "$apk_location"release/app-release.apk "$apk_location"release/weather.apk
			adb uninstall org.woheller69.weather
			adb shell settings put global package_verifier_enable 0
			adb install "$apk_location"release/weather.apk
			adb shell cmd package compile -m speed -f org.woheller69.weather
			rm -r "$apk_location"release
			rm -r "$apk_location"build

		fi
		
        adb shell "rm -rf /sdcard/Documents/oatFolder/oat/arm64/*"
        adb shell "rm -r /sdcard/Documents/MainApp.db"
        adb shell "rm -r /sdcard/Documents/SideScan.db"


        adb shell "am force-stop org.woheller69.weather"
        adb shell "logcat -c"

        adb push ${root_folder_path}config/config.out /data/local/tmp
		adb shell "am start -n "org.woheller69.weather/org.woheller69.weather.activities.SplashActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"

        java -classpath ${root_folder_path}tools/AndroidCFI/target/classes logreader.AdbLogReader 1

        adb shell oatdump --oat-file=/sdcard/Documents/oatFolder/oat/arm64/base.odex  > ${root_folder_path}oatdump.out

        java -classpath ${root_folder_path}tools/AndroidCFI/target/classes odexanalyser.OdexParser


        # waiting till the automation is over
        java -classpath ${root_folder_path}tools/AndroidCFI/target/classes logreader.AdbLogReader
        threshold=$?
        echo "ls command exit stats - $threshold"
        adb shell "am force-stop org.woheller69.weather"

        # pulling the logs out
        adb logcat -d  > ${root_folder_path}log.out

        adb pull sdcard/Documents/SideScan.db ${root_folder_path}db/

        adb pull sdcard/Documents/MainApp.db ${root_folder_path}db/

        sqlite3 ${root_folder_path}db/MainApp.db 'select * from Ground_Truth_AOP order by Start_Count' > ${root_folder_path}db/ground_truth_full.out
        sqlite3 ${root_folder_path}db/SideScan.db 'select * from Side_Channel_Info order by Count' > ${root_folder_path}db/side_channel_info_full.out
		
		java -classpath ${root_folder_path}tools/AndroidCFI/target/classes:/home/ishadi/.m2/repository/org/codehaus/jtstand/jtstand-common/1.5.2/jtstand-common-1.5.2.jar:/home/ishadi/.m2/repository/org/jfree/jfreechart/1.5.0/jfreechart-1.5.0.jar resultanalyser.ResultAnalyser




