# GPS Logger for GU-502MGG-USB
# USBからNMEAデータを受信し、PostgreSQLに保存
# タイムゾーンはロケールからzoneinfoで自動判定し、UTCからローカル時刻に変換

import serial
import psycopg2
import re
from datetime import datetime, timezone
import time
import os
from zoneinfo import ZoneInfo

from config import DATABASE_CONFIG, SERIAL_PORT, BAUDRATE, DB_LOG_INTERVAL

# シリアルポート自動判定
def guess_serial_port():
    if SERIAL_PORT:
        return SERIAL_PORT
    # Windows
    if os.name == "nt":
        return "COM3"
    # Linux/Raspberry Pi
    # 一般的なUSB-シリアル変換器
    for port in ["/dev/ttyUSB0", "/dev/ttyACM0"]:
        if os.path.exists(port):
            return port
    # デフォルト値
    return "/dev/ttyUSB0"

# zoneinfo /etc/timezoneからタイムゾーン名取得
def get_local_zoneinfo():
    tz_name = None
    try:
        with open("/etc/timezone") as f:
            tz_name = f.read().strip()
    except Exception:
        tz_name = None
    if not tz_name or tz_name == 'Etc/UTC':
        # localeや環境変数で判定の拡張可
        tz_name = 'Asia/Tokyo'
    try:
        return ZoneInfo(tz_name)
    except Exception:
        return ZoneInfo('Asia/Tokyo')

LOCAL_ZONE = get_local_zoneinfo()

# NMEAパターン(GGA/RMCセンテンス)
GGA_PATTERN = re.compile(r"\$GPGGA,([0-9]{6}\.\d+),([0-9]{2})([0-9]{2}\.\d+),([NS]),([0-9]{3})([0-9]{2}\.\d+),([EW]),.*")
RMC_PATTERN = re.compile(r"\$GPRMC,([0-9]{6}\.\d+),[AV],([0-9]{2})([0-9]{2}\.\d+),([NS]),([0-9]{3})([0-9]{2}\.\d+),([EW]),.*,(\d{6}),.*")

# 緯度経度変換
def nmea_to_decimal(degree, minute, direction):
    value = float(degree) + float(minute) / 60.0
    if direction in ['S', 'W']:
        value = -value
    return value

# DBテーブル作成(存在しない場合のみ)
def create_table_if_not_exists(conn):
    with conn.cursor() as cur:
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS gps_log (
                id SERIAL PRIMARY KEY,
                latitude DOUBLE PRECISION NOT NULL,
                longitude DOUBLE PRECISION NOT NULL,
                timestamp TIMESTAMP NOT NULL
            )
            """
        )
        conn.commit()

# DB登録
def insert_gps_data(conn, latitude, longitude, timestamp):
    with conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO gps_log (latitude, longitude, timestamp)
            VALUES (%s, %s, %s)
            """,
            (latitude, longitude, timestamp)
        )
        conn.commit()

def main():
    conn = psycopg2.connect(**DATABASE_CONFIG)
    create_table_if_not_exists(conn)
    ser = None
    print('GPS Logger started. Waiting for serial port connection...')
    last_logged_time = None
    port_name = guess_serial_port()
    try:
        while True:
            # シリアルポート未接続なら接続を試みる
            if ser is None or not ser.is_open:
                try:
                    ser = serial.Serial(port_name, BAUDRATE, timeout=1)
                    print(f'Serial port {port_name} connected.')
                except Exception as e:
                    print(f'Waiting for serial port {port_name}... ({e})')
                    time.sleep(2)
                    continue
            try:
                line = ser.readline().decode(errors='ignore').strip()
            except Exception as e:
                print(f'Serial port error: {e}')
                ser.close()
                ser = None
                time.sleep(2)
                continue
            log_this = False
            timestamp = None
            lat = None
            lon = None
            if line.startswith('$GPGGA') or line.startswith('$GNGGA'):
                m = GGA_PATTERN.match(line.replace('$GNGGA', '$GPGGA'))
                if m:
                    hhmmss = m.group(1)[:6]
                    lat = nmea_to_decimal(m.group(2), m.group(3), m.group(4))
                    lon = nmea_to_decimal(m.group(5), m.group(6), m.group(7))
                    now_utc = datetime.now(timezone.utc)
                    timestamp_utc = now_utc.replace(hour=int(hhmmss[:2]), minute=int(hhmmss[2:4]), second=int(hhmmss[4:6]), microsecond=0)
                    timestamp = timestamp_utc.astimezone(LOCAL_ZONE)
                    log_this = True
            elif line.startswith('$GPRMC') or line.startswith('$GNRMC'):
                m = RMC_PATTERN.match(line.replace('$GNRMC', '$GPRMC'))
                if m:
                    hhmmss = m.group(1)[:6]
                    lat = nmea_to_decimal(m.group(2), m.group(3), m.group(4))
                    lon = nmea_to_decimal(m.group(5), m.group(6), m.group(7))
                    date_str = m.group(8)  # DDMMYY
                    day = int(date_str[:2])
                    month = int(date_str[2:4])
                    year = 2000 + int(date_str[4:6])
                    timestamp_utc = datetime(year, month, day, int(hhmmss[:2]), int(hhmmss[2:4]), int(hhmmss[4:6]), tzinfo=timezone.utc)
                    timestamp = timestamp_utc.astimezone(LOCAL_ZONE)
                    log_this = True
            # 指定秒ごとにのみDB登録
            if log_this and timestamp:
                if (last_logged_time is None) or ((timestamp - last_logged_time).total_seconds() >= DB_LOG_INTERVAL):
                    insert_gps_data(conn, lat, lon, timestamp.replace(tzinfo=None))  # DBはnaive datetime（ローカル時刻で格納）
                    print(f"LOGGED: {timestamp}, {lat}, {lon}")
                    last_logged_time = timestamp
            time.sleep(0.1)
    except KeyboardInterrupt:
        print('Logger stopped.')
    finally:
        if ser is not None and ser.is_open:
            ser.close()
        conn.close()

if __name__ == '__main__':
    main()