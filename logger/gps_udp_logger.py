import socket
from insert_gps_data import insert_gps_data_csv_via_json

HOST = ''
PORT = 50052

with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
    s.bind((HOST, PORT))
    print('UDPサーバー起動')
    while True:
        data, addr = s.recvfrom(1024)
        print('受信:', data.decode())
        data_str = data.decode('utf-8').strip()
        insert_gps_data_csv_via_json(data_str, table_name="gps_android_udp_log")
