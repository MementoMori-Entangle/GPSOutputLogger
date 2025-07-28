# GPSOutputLogger

GPSデータの記録と出力を行う  
記録 : logger  
出力 : output  

GPSデータの発生元は3通りを想定  
1. GPS受信機が取得したデータをUSB経由で送信  
2. スマートフォンなどが取得したGPSデータをアプリ(*1)がBluetooth経由で送信 → 実装未定(*2)
3. Wi-Fiインターネットや送信先端末のテザリングAP化で送信

*1はGoogle Play「Bluetooth GPS Output」を利用することも考えましたが、    
Android13では使用できませんでした。  
古いAndroidで動くようだけど、セキュリティ的に問題  
(GPS2Bluetoothなども同様)

*2 SPP/BLEともにAndroidからラズパイへの接続までは問題ないが、  
データを送信すると強制切断される問題が解決できない。  
複数のWi-Fi2.4G通信が常に近くで発生しているが、基本的には影響ない?  
Bluetoothについて詳しい仕様を別途調査してから対応したい。

GPS受信機  
USB接続タイプ みちびき2機(194/195)対応 GU-502MGG-USB  
<img width="300" height="400" alt="GU-502MGG-USB" src="https://github.com/user-attachments/assets/8124e6c0-bfa0-463a-b768-359a96cd96ad" />

通信遷移  
1. GPS受信機  
[GU-502MGG-USB]  
   ↓ Windows : COM3 , Linux : /dev/ttyUSB0  
[GPSを持たない端末]

2. スマートフォン  
[Androidアプリ]  
   ↓ Bluetooth SPP/BLE  
[GPSを持たない端末]

3. スマートフォン  
[Androidアプリ]  
   ↓ Wi-Fi HTTP/HTTPS/TCP/UDP  
[GPSを持たない端末]

GPSを持たない端末 = Raspberry Pi 4 ModelB  
ラズパイの設定などは  
https://github.com/MementoMori-Entangle/DecibelMonitoringService  
をベースとしています。

# 出力(output)
開発環境  
AndroidStudio JDK11

リポジトリ管理外ファイル  
my-release-key.jks  
keystore.properties  
local.properties  
ca.crt  
server.crt  
server.key

実行環境  
Android13

# 記録(logger)
開発環境  
Python 3.13.3  
PostgreSQL 17.5 on x86_64-windows, compiled by msvc-19.44.35209, 64-bit  

実行環境  
Python 3.11.2  
psql (PostgreSQL) 15.13 (Debian 15.13-0+deb12u1)

Pythonパッケージ : pyserial psycopg2 flask

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
