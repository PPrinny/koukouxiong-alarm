# 扣扣熊提醒系统 - 代码说明

## 系统架构

```
┌─────────────────┐                    ┌─────────────────┐
│    云电脑服务端   │ ◄─── WebSocket ───► │   安卓APK客户端  │
│  (server.py)    │                    │  (AlarmService)  │
│                 │                    │                  │
│  - HTTP API     │                    │  - 后台运行       │
│  - WebSocket    │                    │  - 接收指令       │
│  - 定时触发      │                    │  - 响铃+震动      │
└─────────────────┘                    └─────────────────┘
        ▲
        │ HTTP API调用
        ▼
┌─────────────────┐
│    扣子/我       │
│  设置提醒时间    │
└─────────────────┘
```

---

## 快速开始

### 1. 一键部署
```bash
chmod +x deploy.sh && ./deploy.sh
```

**输出示例**：
```
Server started with PID: 12345
Server is running!
Building APK...
✅ APK built successfully!
📦 APK path: ./alarm_android/app/build/outputs/apk/release/app-release.apk
🔧 Server running in background (PID: 12345)
📝 Server logs in: ./server.log
```

---

## 文件结构

### 服务端（云电脑运行）

```
alarm_server/
├── server.py          # 主服务文件
├── requirements.txt   # 依赖声明
└── server.log         # 运行日志（自动生成）
```

**功能**：
- WebSocket服务 (端口8766)：与APK保持长连接
- HTTP API (端口8765)：供我调用来设置提醒

**API接口**：
| 接口 | 说明 | 示例 |
|------|------|------|
| GET /health | 检查服务状态 | /health |
| GET /set | 设置提醒 | /set?time=2026-04-20T08:00:00&amp;message=起床 |
| GET /cancel?id=xxx | 取消提醒 | /cancel?id=alarm_xxx |
| GET /trigger | 立即触发测试 | /trigger?message=测试 |

---

### 安卓APK（手机安装）

```
alarm_android/
├── build.gradle                    # 项目构建配置
├── settings.gradle.kts             # 项目设置
├── app/
│   ├── build.gradle.kts           # APP构建配置
│   └── src/main/
│       ├── AndroidManifest.xml    # 权限声明
│       ├── res/
│       │   ├── layout/
│       │   │   ├── activity_main.xml   # 主界面布局
│       │   │   └── activity_alarm.xml  # 响铃界面布局
│       │   └── values/
│       │       └── styles.xml     # 主题样式
│       └── java/com/koukouxiong/alarm/
│           ├── MainActivity.kt    # 主界面（配置服务器）
│           ├── AlarmService.kt    # 核心服务
│           └── AlarmActivity.kt   # 响铃界面
```

**权限申请**：
- INTERNET：网络连接
- VIBRATE：震动
- WAKE_LOCK：唤醒屏幕
- FOREGROUND_SERVICE：后台服务

**注意**：没有申请任何隐私权限（通讯录、定位、相机等）

---

## 工作流程

1. **一键部署**（已完成）
   - 启动服务端
   - 编译APK

2. **安装APK到手机**
   - 连接手机到电脑
   - 使用ADB安装：`adb install ./alarm_android/app/build/outputs/apk/release/app-release.apk`

3. **配置APK**
   - 打开APP
   - 输入云电脑的IP地址（如：192.168.1.100）
   - 点击"连接服务器"

4. **设置提醒**（我来操作）
   ```
   GET http://云电脑IP:8765/set?time=2026-04-20T08:00:00&message=起床啦
   ```

5. **触发提醒**
   - 到时间，服务端推送消息给APK
   - APK开始响铃+震动
   - 显示全屏界面
   - 用户点击"停止响铃"

---

## 安全说明

### 数据传输
- WebSocket使用自定义协议
- 只传输提醒消息，不传任何隐私
- 消息格式：`{"type": "alarm", "alarm_id": "xxx", "message": "起床啦"}`

### 权限最小化
- APP只申请必要的权限
- 没有后台偷偷上传数据的代码
- 没有任何第三方SDK（除了WebSocket库）

### 代码透明
- 所有代码都是你可见的
- 没有混淆（可以看懂逻辑）
- 可以自行审计

---

## 后续步骤

1. **安装APK到手机**（你操作）
2. **配置服务器连接**（你操作）
3. **测试提醒功能**（我来操作）
4. **集成到扣子日程**（我来操作）

---

## 常见问题

**Q: 手机息屏后还能收到提醒吗？**
A: 可以，使用了前台服务(Foreground Service)和唤醒锁(WakeLock)

**Q: 会被系统杀掉吗？**
A: 前台服务不容易被杀，建议在电池设置中关闭对该APP的优化

**Q: 需要联网吗？**
A: 需要，手机和云电脑要在同一网络，或者云电脑有公网IP

**Q: 耗电吗？**
A: 很少，WebSocket长连接耗电极低

**Q: 可以在外网使用吗？**
A: 可以，需要云电脑有公网IP，或者使用内网穿透