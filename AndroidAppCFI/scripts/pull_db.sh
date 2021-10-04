    #!/bin/bash

   # pulling the logs out
    adb logcat -d  > /home/ishadi/Documents/AndroidCFI/apps/weather/v1/log.out

    adb pull sdcard/Documents/SideScan.db /home/ishadi/Documents/AndroidCFI/apps/weather/v1/db/
	
	adb pull sdcard/Documents/MainApp.db /home/ishadi/Documents/AndroidCFI/apps/weather/v1/db/
	
	
	cd /home/ishadi/Documents/AndroidCFI/apps/weather/v1/db/
	
	sqlite3 MainApp.db 'select * from Ground_Truth_AOP order by Start_Count' > /home/ishadi/Documents/AndroidCFI/apps/weather/v1/db/ground_truth_full.out
	sqlite3 SideScan.db 'select * from Side_Channel_Info order by Count' > /home/ishadi/Documents/AndroidCFI/apps/weather/v1/db/side_channel_info_full.out
