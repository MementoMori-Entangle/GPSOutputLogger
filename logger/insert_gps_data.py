import psycopg2
from datetime import datetime, timezone
from zoneinfo import ZoneInfo
from config import DATABASE_CONFIG

# タイムゾーン取得(ローカル環境のタイムゾーンを取得、失敗時はAsia/Tokyo)
def get_local_zoneinfo():
    try:
        with open("/etc/timezone") as f:
            tz_name = f.read().strip()
            if tz_name and tz_name != 'Etc/UTC':
                return ZoneInfo(tz_name)
    except Exception:
        pass
    return ZoneInfo('Asia/Tokyo')

LOCAL_ZONE = get_local_zoneinfo()

# DBテーブル作成(存在しない場合のみ)
def create_table_if_not_exists(conn, table_name):
    with conn.cursor() as cur:
        cur.execute(
            f"""
            CREATE TABLE IF NOT EXISTS {table_name} (
                id SERIAL PRIMARY KEY,
                latitude DOUBLE PRECISION NOT NULL,
                longitude DOUBLE PRECISION NOT NULL,
                timestamp TIMESTAMP NOT NULL
            )
            """
        )
        conn.commit()

def csv_line_to_json_dict(line):
    """
    line: "lat,lon,unixtime" 例: "43.0533567,141.3273817,1753689233771"
    return: dict形式 {'lat': ..., 'lon': ..., 'time': ...}
    """
    try:
        lat_str, lon_str, time_str = line.strip().split(',')
        return {
            'lat': float(lat_str),
            'lon': float(lon_str),
            'time': int(time_str)
        }
    except Exception as e:
        print(f"Parse error: {e}, input: {line}")
        return None

def insert_gps_data_csv_via_json(line, table_name="gps_log"):
    """
    カンマ区切り文字列をJSON化し、既存のinsert_gps_data_dictでDB登録
    """
    gps_json = csv_line_to_json_dict(line)
    if gps_json is not None:
        insert_gps_data_dict(gps_json, table_name=table_name)
    else:
        print("登録失敗: データ不正")

# GPSデータ(dict形式)をDBへ登録
def insert_gps_data_dict(data, table_name="gps_log"):
    """
    data: {'lat': float, 'lon': float, 'time': int}  # timeはUnixミリ秒
    table_name: 登録先のテーブル名(デフォルト: gps_log)
    """
    lat = float(data['lat'])
    lon = float(data['lon'])
    # Unixミリ秒→datetime(ローカルタイムゾーン)
    dt_utc = datetime.fromtimestamp(int(data['time']) / 1000, tz=timezone.utc)
    dt_local = dt_utc.astimezone(LOCAL_ZONE).replace(tzinfo=None)  # DBにはnaive datetimeで格納

    conn = psycopg2.connect(**DATABASE_CONFIG)
    create_table_if_not_exists(conn, table_name)
    with conn.cursor() as cur:
        cur.execute(
            f"""
            INSERT INTO {table_name} (latitude, longitude, timestamp)
            VALUES (%s, %s, %s)
            """,
            (lat, lon, dt_local)
        )
        conn.commit()
    conn.close()
    print(f"LOGGED: {dt_local}, {lat}, {lon} into table {table_name}")

# 使用例
if __name__ == "__main__":
    sample_data = {'lat': 43.053384, 'lon': 141.3273865, 'time': 1753689267025}
    insert_gps_data_dict(sample_data, table_name="gps_log_test")
