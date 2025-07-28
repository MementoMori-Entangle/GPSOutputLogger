import socket
from insert_gps_data import insert_gps_data_csv_via_json

HOST = ''
PORT = 50052

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
    s.bind((HOST, PORT))
    s.listen()
    print('TCP�T�[�o�[�N��')
    while True:
        conn, addr = s.accept()
        with conn:
            print('�ڑ�:', addr)
            while True:
                data = conn.recv(1024)
                if not data:
                    break  # �ڑ����؂ꂽ�烋�[�v�𔲂��Ď���accept��
                print('��M:', data.decode())
                data_str = data.decode('utf-8').strip()
                insert_gps_data_csv_via_json(data_str, table_name="gps_android_tcp_log")
