from flask import Flask, request, jsonify
from insert_gps_data import insert_gps_data_dict

HOST = '0.0.0.0'
PORT = 50052

app = Flask(__name__)

@app.route('/gps', methods=['POST'])
def receive_gps():
    data = request.get_json()
    print(f"受信: {data}")
    insert_gps_data_dict(data, table_name="gps_android_https_log")
    return jsonify({'status': 'ok'})

if __name__ == '__main__':
    # サーバ証明書と秘密鍵のパスを指定
    context = ('server.crt', 'server.key')
    app.run(host=HOST, port=PORT, ssl_context=context)
