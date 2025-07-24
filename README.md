# GPSOutputLogger

GPSデータの記録と出力を行う  
記録 : logger  
出力 : output  

GPSデータの発生元は2通りを想定  
1. GPS受信機が取得したデータをUSB経由で送信  
2. スマートフォンなどが取得したGPSデータをアプリ(*1)がBluetooth経由で送信

*1はGoogle Play「Bluetooth GPS Output」を利用することも考えましたが、    
Android13では使用できませんでした。  
古いAndroidで動くようだけど、セキュリティ的に問題  
(GPS2Bluetoothなども同様)

GPS受信機  
USB接続タイプ みちびき2機(194/195)対応 GU-502MGG-USB

通信遷移  
1. GPS受信機  
[GU-502MGG-USB]  
   ↓ Windows : COM3 , Linux : /dev/ttyUSB0  
[GPSを持たない端末]

2. スマートフォン  
[Androidアプリ]  
   ↓ Bluetooth SPP/BLE  
[GPSを持たない端末]

# 記録(logger)
開発環境  
Python 3.13.3  
PostgreSQL 17.5 on x86_64-windows, compiled by msvc-19.44.35209, 64-bit  

実行環境  
Python 3.11.2  
psql (PostgreSQL) 15.13 (Debian 15.13-0+deb12u1)

Pythonパッケージ : pyserial psycopg2

#ユーザーとDB作成  
CREATE USER "GPSLogger" WITH PASSWORD 's#gs1Gk3Dh8sa!g3s';  
CREATE DATABASE "GPSMonitor" OWNER "GPSLogger";

#権限追加  
GRANT CONNECT ON DATABASE "GPSMonitor" TO "GPSLogger";  
GRANT USAGE, CREATE ON SCHEMA public TO "GPSLogger";  
ALTER DEFAULT PRIVILEGES IN SCHEMA public  
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "GPSLogger";

# 出力(output)
Bluetooth通信がこんなに面倒だったなんて四苦八苦
