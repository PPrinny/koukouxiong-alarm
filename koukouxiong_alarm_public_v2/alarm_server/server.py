#!/usr/bin/env python3
"""
提醒服务端 - 运行在云电脑
功能：
1. 接收提醒设置请求
2. 到时间推送响铃指令给APK
"""

import asyncio
import json
import time
import hmac
import hashlib
import os
from datetime import datetime
from typing import Dict, Set
import websockets
from websockets.server import serve
from http.server import HTTPServer, BaseHTTPRequestHandler
import threading
import urllib.parse

# ==================== 配置 ====================

TOKEN = os.environ.get("ALARM_TOKEN", "")  # 请设置环境变量 ALARM_TOKEN 或修改此处
WS_PORT = 8766
HTTP_PORT = 8765

# ==================== 全局状态 ====================

connected_clients: Set[websockets.WebSocketServerProtocol] = set()
alarms: Dict[str, dict] = {}
loop = None


# ==================== 安全工具 ====================

def sign_message(data: dict) -> dict:
    payload = f"{data.get('type','')}|{data.get('alarm_id','')}|{data.get('message','')}|{data.get('timestamp','')}"
    sig = hmac.new(TOKEN.encode(), payload.encode(), hashlib.sha256).hexdigest()
    data["signature"] = sig
    return data


def verify_signature(data: dict) -> bool:
    sig = data.pop("signature", None)
    if not sig:
        return False
    payload = f"{data.get('type','')}|{data.get('alarm_id','')}|{data.get('message','')}|{data.get('timestamp','')}"
    expected = hmac.new(TOKEN.encode(), payload.encode(), hashlib.sha256).hexdigest()
    return hmac.compare_digest(sig, expected)


# ==================== WebSocket 处理 ====================

async def handle_client(websocket, path):
    parsed = urllib.parse.urlparse(path)
    params = urllib.parse.parse_qs(parsed.query)
    client_token = params.get("token", [None])[0]
    if client_token != TOKEN:
        print(f"[{datetime.now()}] 客户端认证失败，拒绝连接")
        await websocket.close(4001, "Unauthorized")
        return

    client_id = id(websocket)
    connected_clients.add(websocket)
    print(f"[{datetime.now()}] 客户端连接: {client_id}, 当前: {len(connected_clients)}")
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
                if data.get("type") == "alarm_stopped":
                    print(f"[{datetime.now()}] 用户停止闹钟: {data.get('alarm_id')}")
            except json.JSONDecodeError:
                print(f"[{datetime.now()}] 收到无效消息")
    except websockets.exceptions.ConnectionClosed:
        pass
    except Exception as e:
        print(f"[{datetime.now()}] WebSocket异常: {e}")
    finally:
        connected_clients.discard(websocket)
        print(f"[{datetime.now()}] 客户端断开: {client_id}")


async def broadcast(message: dict):
    if not connected_clients:
        return False
    message["timestamp"] = int(time.time())
    message = sign_message(message)
    msg_str = json.dumps(message)
    for client in list(connected_clients):
        try:
            await client.send(msg_str)
        except Exception:
            connected_clients.discard(client)
    return True


async def alarm_checker():
    while True:
        now = time.time()
        for alarm_id, alarm in list(alarms.items()):
            if alarm["time"] <= now:
                print(f"[{datetime.now()}] 触发: {alarm_id}")
                await broadcast({
                    "type": "alarm",
                    "alarm_id": alarm_id,
                    "message": alarm["message"]
                })
                del alarms[alarm_id]
        await asyncio.sleep(1)


# ==================== HTTP API ====================

class APIHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def _check_token(self) -> bool:
        parsed = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        token = query.get("token", [None])[0]
        if token != TOKEN:
            self._error(403, "认证失败")
            return False
        return True

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path, query = parsed.path, urllib.parse.parse_qs(parsed.query)

        if path == "/health":
            self._json({"status": "ok", "clients": len(connected_clients)})
            return

        if not self._check_token():
            return

        if path == "/alarms":
            safe_alarms = {k: {"time": v["time"], "message": v["message"]} for k, v in alarms.items()}
            self._json({"alarms": safe_alarms, "count": len(alarms)})

        elif path == "/set":
            time_str = query.get("time", [None])[0]
            message = query.get("message", ["时间到了"])[0]
            if not time_str:
                self._error(400, "缺少time参数")
                return
            try:
                t = datetime.fromisoformat(time_str).timestamp()
                if t <= time.time():
                    self._error(400, "时间不能是过去")
                    return
                aid = f"alarm_{int(time.time()*1000)}_{os.urandom(4).hex()}"
                alarms[aid] = {"time": t, "message": message}
                self._json({"success": True, "alarm_id": aid, "seconds_until": int(t - time.time())})
            except Exception as e:
                self._error(400, str(e))

        elif path == "/cancel":
            aid = query.get("id", [None])[0]
            if aid in alarms:
                del alarms[aid]
                self._json({"success": True})
            else:
                self._error(404, "不存在")

        elif path == "/trigger":
            msg = query.get("message", ["测试"])[0]
            aid = f"test_{int(time.time()*1000)}_{os.urandom(4).hex()}"
            try:
                asyncio.run_coroutine_threadsafe(
                    broadcast({"type": "alarm", "alarm_id": aid, "message": msg}), loop
                ).result(timeout=5)
                self._json({"success": True, "clients": len(connected_clients)})
            except Exception as e:
                self._error(500, f"触发失败: {e}")

        else:
            self._error(404, "未知路径")

    def _json(self, data):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def _error(self, code, msg):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps({"error": msg}).encode())


def run_http():
    HTTPServer(("0.0.0.0", HTTP_PORT), APIHandler).serve_forever()


async def main():
    global loop
    loop = asyncio.get_event_loop()
    threading.Thread(target=run_http, daemon=True).start()
    asyncio.create_task(alarm_checker())
    print(f"服务端启动: WS={WS_PORT}, HTTP={HTTP_PORT}")
    print(f"Token: {TOKEN[:8]}...")
    async with serve(handle_client, "0.0.0.0", WS_PORT):
        await asyncio.Future()


if __name__ == "__main__":
    asyncio.run(main())
