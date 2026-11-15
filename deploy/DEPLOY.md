# 家庭房源 · 部署手册（Windows 部署机）

> 适用：把系统从开发机搬到家里常开的 Windows 机器（如华硕天选2），局域网内家人用手机/电脑访问。

---

## 0. 先回答「是不是只要一个 jar」

**运行程序本身：是的，只要一个 fat jar**（约 52 MB），前端页面、后端代码、依赖库全部内嵌，不需要 Tomcat、不需要 Nginx、不需要 Node.js、不需要 Maven。

**但要让系统跑起来，部署机一共需要这 4 类东西 —— 已全部集中在项目的 `deploy/` 文件夹里，整个文件夹拷过去即可：**

| # | 交付物 | 在 `deploy/` 里 | 说明 |
|---|---|---|---|
| 1 | `rent-system-java-0.0.1-SNAPSHOT.jar` | ✅ 已放（仅本地，不入 Git） | 程序本体，**必须带 `-Pdeploy`** 构建（见 §2.1） |
| 2 | `schema.sql`、`seed-data.sql` | ✅ 已放 | 建表 + 初始数据；**不在 jar 里**，需单独执行 |
| 3 | `application-prod.yml` | 由 `application-prod.yml.example` 复制改名 | 放 jar **同目录**，填数据库密码、成员真实 SendKey；含密钥，不入库 |
| 4 | `start.bat` | ✅ 已放 | 一键启动脚本 |
| — | `DEPLOY.md` | ✅ 本手册 | 部署说明 |

`deploy/` 文件夹内容：

```
deploy/
├─ DEPLOY.md                              ← 本手册
├─ application-prod.yml.example          ← 配置模板（复制改名后填写）
├─ schema.sql                            ← 建表脚本
├─ seed-data.sql                         ← 种子数据
├─ start.bat                             ← 启动脚本
└─ rent-system-java-0.0.1-SNAPSHOT.jar  ← 干净 jar（仅本地，.gitignore 已排除）
```

> `deploy/*.jar` 与 `deploy/application-prod.yml` 已被 `.gitignore` 排除：二进制构建产物和含真实密钥的配置**不进版本库**。从 Git 克隆后若 `deploy/` 里没有 jar，按 §2.1 重新打包并拷入即可。

**环境依赖（装在部署机上，不是交付文件）**：JDK 21、MySQL 8.0。

> ⚠️ **务必用 `-Pdeploy` 打包**。默认 `package` 会把开发机上的 `src/main/resources/application-local.yml` 一起打进 jar，里面含开发期真实 SendKey 且 `mock: true`（不真实推送）。带 `-Pdeploy` 会排除它，产出干净 jar。验证方法见 §6。

---

## 1. 部署机环境准备

### 1.1 安装 JDK 21
- 下载 Temurin JDK 21（https://adoptium.net），一路默认安装。
- 验证：
  ```cmd
  java -version
  ```
  应显示 `openjdk version "21.x.x"`。

### 1.2 安装 MySQL 8.0
- 安装 MySQL 8.0（MySQL Installer 或解压版均可），记住 **root 密码**。
- 字符集要求：`utf8mb4` / `utf8mb4_0900_ai_ci`（建库脚本已指定，无需手工设置 my.ini）。
- 验证：
  ```cmd
  mysql -u root -p -e "SELECT VERSION();"
  ```

### 1.3 防火墙放行 8080
手机要能访问，需放行入站 TCP 8080：
```cmd
netsh advfirewall firewall add rule name="家庭房源 8080" dir=in action=allow protocol=TCP localport=8080
```
（或用「Windows  Defender 防火墙 → 入站规则 → 新建规则 → 端口 → TCP 8080 → 允许」）

### 1.4 建议给部署机固定局域网 IP
在路由器里给这台机器绑定固定 IP（如 `192.168.1.50`），家人访问地址就是 `http://192.168.1.50:8080`。

---

## 2. 准备部署目录与 jar

### 2.1 在开发机打干净 jar
```bash
cd rent-system-java
# 1) 先确保前端产物是最新（若前端有改动才需要）
cd frontend && npm install && npm run build && cd ..
# 2) 关键：-Pdeploy 排除 application-local.yml
./mvnw -Pdeploy -DskipTests clean package
```
产物：`target/rent-system-java-0.0.1-SNAPSHOT.jar`（已同步拷入 `deploy/`）

**验证 jar 是干净的**（应只有 `application.yml`，没有 `application-local.yml`）：
```bash
unzip -l deploy/rent-system-java-0.0.1-SNAPSHOT.jar | findstr application
```
Windows 上可用压缩软件直接打开 jar 查看 `BOOT-INF/classes/` 下有无 `application-local.yml`。

### 2.2 部署机上建目录并拷文件
**把整个 `deploy/` 文件夹拷到部署机**，例如 `D:\rent-system\`，然后按 §3 由模板生成配置：

```
D:\rent-system\
├─ rent-system-java-0.0.1-SNAPSHOT.jar   ← deploy/ 已带
├─ application-prod.yml                  ← 由 application-prod.yml.example 复制改名并填写
├─ application-prod.yml.example          ← 配置模板
├─ start.bat                             ← deploy/ 已带
├─ schema.sql                            ← deploy/ 已带
├─ seed-data.sql                         ← deploy/ 已带
└─ DEPLOY.md                             ← 本手册
```

> `start.bat` 会自动找**同目录**的 jar 并默认激活 `prod` profile，不需要 `target\` 目录。

---

## 3. 写配置文件 `application-prod.yml`

放在 **jar 同目录**。Spring Boot 会自动加载同目录的 `application-<profile>.yml`，优先级高于 jar 内配置。

**直接复制 `deploy/application-prod.yml.example` 为 `application-prod.yml`，替换其中所有 `<...>` 占位值即可**（该文件与本节模板一致，且带注释说明）。生成后的 `application-prod.yml` 含真实密钥，已被 `.gitignore` 排除，切勿提交或外发。

**模板全文：**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/rent_system?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_ai_ci&serverTimezone=Asia/Shanghai
    username: root
    password: <MySQL root 密码>

rent-system:
  security:
    # SendKey 加密口令：自己定一个长随机串，首次设定后【永不更改】
    secret: <自定义长口令，如 32 位随机串>
  notify:
    mock: false                    # false = 真实推送；true = 假推送（仅调试）
    # wecom-webhook: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx   # 可选降级通道
  init:
    # 家庭成员真实账号：启动时自动加密入库（明文只存在于内存中）
    members:
      - username: admin
        send-key: <Server酱 SendKey>
        phone: ""
        digest-send-time: "09:00"
      - username: mama
        send-key: <Server酱 SendKey>
        phone: ""
        digest-send-time: "09:00"
      - username: xiaoming
        send-key: <Server酱 SendKey>
        phone: ""
        digest-send-time: "09:00"
```

### 3.1 配置项说明

| 配置 | 必填 | 说明 |
|---|---|---|
| `spring.datasource.url` | ✅ | MySQL 不是本机/不是 3306 时改这里；库名固定 `rent_system` |
| `spring.datasource.username/password` | ✅ | 也可用环境变量 `RENT_DB_USER` / `RENT_DB_PASSWORD` 代替 |
| `rent-system.security.secret` | ✅ | **加密口令，首次设定后不可更改**。改了之后已入库的 SendKey 密文全部解不开，表现为「登录失败 / 推送失败」。忘记口令只能清空 `member` 表重建 |
| `rent-system.notify.mock` | ✅ | `false` 真实调用 Server酱；`true` 不调外网直接记 success（调试用） |
| `rent-system.notify.wecom-webhook` | ⬜ | Server酱失败时的降级通道；留空则失败记为 `failed`，可在通知记录页补发 |
| `rent-system.init.members[].send-key` | ✅ | 每位家人自己的 Server酱 SendKey（https://sct.ftqq.com 获取） |
| `rent-system.init.members[].digest-send-time` | ⬜ | 该成员每日推送时间，**只支持整点**（如 `09:00`）；非整点顺延到下一整点。默认 `09:00` |

> ⚠️ **`members` 是整组替换，不是追加**：一旦在 `application-prod.yml` 里写了 `init.members`，jar 内 `application.yml` 的 3 个演示账号配置就不再生效。
> 因此**请把全部家庭成员都列在这里**，漏掉的人不会自动建号。已实测：只写 1 个账号时启动日志为「新建 1 个」。
>
> 另外，`seed-data.sql` 已预置 `admin / mama / xiaoming` 三个占位成员（哈希为 `__REPLACE...`）。启动时 `MemberInitializer` 会用同名配置把它们刷新成真实密文，日志显示「刷新占位 3 个」。
> 若你在 prod 里改用了**别的名字**，这三个占位成员会保留占位哈希而无法登录（可在库中删除，或保留用户名与种子一致）。
>
> ⚠️ **`secret` 与已有数据库必须配套（实测坑）**：`MemberInitializer` 对「已初始化」的成员是**跳过不覆盖**的（保护界面上改过的推送开关/时间），只有哈希为 `__REPLACE...` 的占位成员才会被刷新。
> 所以若把**已经跑过的数据库**搬到一台换了新 `secret` 的机器，旧密文与新密钥不匹配，登录会返回 `用户名或 SendKey 不正确`，日志却是「跳过已初始化 3 个」——看起来一切正常但谁也登不进去。
> 部署机请按 §4 **全新执行 `schema.sql` + `seed-data.sql`**（占位成员会被自动刷新）；若确需迁移旧库，要么沿用原 `secret`，要么先清空 `member` 表再启动。

### 3.2 关于 SendKey 与登录
- 登录用「用户名 + 本人 SendKey」，明文 SendKey 不落库：库里只存 AES-GCM 密文、HMAC 校验值、尾四位。
- 启动时 `MemberInitializer` 会自动把上面 `members` 里的账号加密建号；**已存在且已初始化的成员不会被覆盖**（界面上改过的推送开关/时间会保留）。
- 前端登录页的「一键登录」快捷账号是**编译进前端 JS 的演示账号**（`admin/mama/xiaoming`，对应演示 SendKey）。若部署机把 SendKey 换成真实值，快捷按钮会失效，此时**手工输入用户名 + 真实 SendKey 登录**即可；如需保留一键登录，需改 `frontend/.env` 里的 `VITE_QUICK_ACCOUNTS` 后重新 `npm run build` 并重新打包 jar。

### 3.3 关于 `system_config`
`seed-data.sql` 已写入全局默认：`digest_send_time=09:00`、`remind_advance_days=7`、`send_on_empty_day=0`（无事项日不发，省 Server酱 额度）。如需「每天必发」，执行：
```sql
UPDATE system_config SET config_value='1' WHERE config_key='send_on_empty_day';
```

---

## 4. 执行 SQL（建库 + 种子数据）

**顺序不能反**，在部署机 CMD 中：

```cmd
cd /d D:\rent-system
mysql -u root -p < schema.sql
mysql -u root -p < seed-data.sql
```

说明：
- `schema.sql` 自带 `CREATE DATABASE IF NOT EXISTS rent_system` 与 `USE rent_system`，**不需要手工建库**。
- 脚本全部是 `IF NOT EXISTS` / `ON DUPLICATE KEY UPDATE`，可重复执行、不破坏已有数据。
- ⚠️ **不要执行 `schema.sql` 末尾的「可选：重建区块」**（那会 DROP 全部表清空数据）。
- `schema.sql` 建 **8 张表**：`house`、`lease_contract`、`member`、`digest_template`、`digest_report`、`notification_record`、`reminder_record`、`system_config`。
- `seed-data.sql` 灌入初始数据：8 套房源（4 已租 / 4 空置）、4 份生效合同 + 1 份违约历史、3 个占位成员、日报模板、系统配置。
- 成员密码是占位串 `__REPLACE...`，**首次启动应用时由 `MemberInitializer` 用 §3 配置的真实 SendKey 自动替换**，日志会显示「刷新占位 3 个」。

验证：
```cmd
mysql -u root -p rent_system -e "SHOW TABLES; SELECT COUNT(*) FROM house; SELECT username, send_key_tail FROM member;"
```

> 若部署机 MySQL 库名/端口与默认不同，同步改 §3 的 `spring.datasource.url`；也可用环境变量 `SPRING_DATASOURCE_URL` 覆盖。

---

## 5. 启动与访问

### 5.1 启动
双击 `start.bat`，或 CMD：
```cmd
cd /d D:\rent-system
set SPRING_PROFILES_ACTIVE=prod
java -jar rent-system-java-0.0.1-SNAPSHOT.jar
```

启动成功的日志标志：
```
The following 1 profile is active: "prod"
Tomcat started on port 8080
Started RentSystemJavaApplication ...
成员初始化完成：新建 0 个、刷新占位 3 个、跳过已初始化 0 个
```

> `start.bat` 已内置 `SPRING_PROFILES_ACTIVE=prod`，无需手工设置。
> 关闭 CMD 窗口即停止服务。要开机自启，可把 `start.bat` 的快捷方式放进「启动」文件夹，或注册为 Windows 服务（如用 NSSM）。

### 5.2 访问
| 设备 | 地址 |
|---|---|
| 部署机本机 | `http://localhost:8080` |
| 家人手机 / 电脑 | `http://<部署机局域网IP>:8080`（如 `http://192.168.1.50:8080`） |

查本机 IP：`ipconfig` → 看「IPv4 地址」。

### 5.3 首次验证清单
1. 打开页面出现登录页 → 输入用户名 + 真实 SendKey 登录成功。
2. 看板显示「总房源 8 / 已出租 4 / 空置中 4」，财产卡有数字。
3. 房源卡片可点开详情；空置卡片点击直接弹「登记出租」表单。
4. 「提醒日历」页月历、临期列表有数据。
5. 「配置管理」页能看到成员、推送开关与时间、日报模板。
6. 推送验证：把某成员推送时间设成**下一个整点**、确认 `mock: false`，到点后微信应收到一条「家庭房源日报」。
   - Server酱免费额度每天 5 次，**联调期建议只测 1–2 次**，测完把推送时间改回常用时间。
   - 推送记录在「提醒日历 → 通知记录」可查，失败可点「补发」。

---

## 6. 检查 jar 是否干净（重要）

```cmd
cd /d D:\rent-system
jar tf rent-system-java-0.0.1-SNAPSHOT.jar | findstr application
```
应只看到 `BOOT-INF/classes/application.yml`。若出现 `application-local.yml`，说明打包时漏了 `-Pdeploy`，请重新按 §2.1 打包。

原因：jar 内 `application.yml` 默认激活 `local` profile，一旦 jar 里残留 `application-local.yml`，会覆盖成 `mock: true`（推送永远不真发）并带入开发机密钥。

---

## 7. 常见故障排查

| 现象 | 原因 / 处理 |
|---|---|
| 启动报 `Schema-validation: missing table` | 没执行 `schema.sql`，或连错数据库。按 §4 重新执行并核对 `spring.datasource.url` |
| 启动报 `Communications link failure` | MySQL 没启动 / 端口不对 / 密码错。先 `mysql -u root -p -e "SELECT 1"` 验证 |
| 登录提示「用户名或 SendKey 不正确」，但日志显示「跳过已初始化 N 个」 | `security.secret` 与数据库已有密文不匹配（旧库搬到新机器时最常见）。恢复原 secret；或清空 `member` 表、重新执行 `seed-data.sql` 后重启，让占位成员按新 secret 重新初始化 |
| 登录提示「用户名或 SendKey 不正确」 | ① §3 的 `members` 没配或 SendKey 写错；② **`security.secret` 改过**，导致 HMAC 对不上 → 同上处理 |
| 推送记录是 success 但微信没收到 | `mock: true` 了。改 `application-prod.yml` 为 `false` 并重启 |
| 到点没推送 | ① 成员 `push_enabled=0`（配置页开关）；② `send_on_empty_day=0` 且当天无空置/临期事项；③ 推送时间只认整点，非整点顺延下一整点；④ 应用当时没运行 |
| 手机打不开页面 | 防火墙没放行 8080（§1.3）；确认手机与部署机在同一 WiFi；用局域网 IP 而非 localhost |
| 端口 8080 被占用 | 改端口：启动时加 `set SERVER_PORT=8081`（防火墙也要同步放行） |
| 界面是旧版本 | 浏览器缓存，强制刷新 `Ctrl+F5`；确认部署机 jar 是最新构建 |
| 重启后需重新登录 | 正常：会话存内存，重启即失效（设计如此，无状态） |

---

## 8. 运维须知

- **备份**：定期备份整个库即可（`mysqldump -u root -p rent_system > backup.sql`）。前端/程序由 jar 提供，无需备份。
- **`secret` 不可变**：`rent-system.security.secret` 决定所有 SendKey 的加密与登录校验。变更 = 全员无法登录且推送解密失败。请把它记在可靠的密码管理器里。
- **会话**：登录态存于 JVM 内存（`ConcurrentHashMap`）+ HttpOnly Cookie，重启应用后所有人需重新登录；不引入 Redis。
- **推送额度**：Server酱免费账号每天 5 条；系统按「每小时整点 tick + 每成员独立时间 + 幂等键」推送，正常情况下每人每天只 1 条。
- **日志**：直接输出到控制台；长期运行建议重定向，如 `java -jar ... >> app.log 2>&1`。
- **升级**：替换 jar 文件 → 重启即可（表结构未变时无需重跑 SQL）。若版本间改了表结构，需另行执行迁移脚本（本项目 `ddl-auto=validate`，不会自动改表）。
