import os

# データベース接続設定
DB_HOST = os.environ.get('DB_HOST', 'localhost')
DB_PORT = os.environ.get('DB_PORT', '5432')
DB_NAME = os.environ.get('DB_NAME', 'GPSMonitor')
DB_USER = os.environ.get('DB_USER', 'GPSLogger')
DB_PASS = os.environ.get('DB_PASS', 's#gs1Gk3Dh8sa!g3s')

# シリアルポート設定
# 空欄なら自動判定(Windows: COM3 / Linux: /dev/ttyUSB0 等)
SERIAL_PORT = ""
BAUDRATE = int(os.environ.get('GPS_BAUDRATE', '9600'))

# データベース登録間隔(秒)
DB_LOG_INTERVAL = int(os.environ.get('GPS_LOG_INTERVAL', '1'))  # 1秒間隔(デフォルト)

DATABASE_CONFIG = {
    'host': DB_HOST,
    'port': DB_PORT,
    'dbname': DB_NAME,
    'user': DB_USER,
    'password': DB_PASS
}