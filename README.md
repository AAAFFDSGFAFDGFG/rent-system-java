# 家庭房源 · 家庭房屋租赁管理系统

家庭自用的局域网房源租赁管理工具：桌面宽屏看板 + 手机卡片流双端界面，单进程一键启动；家人各用「用户名 + 本人 Server酱 SendKey」登录，每日在各自设定时间收到一条聚合日报推送到个人微信。

## 技术栈

- **后端**：Spring Boot 4.1.2 + JDK 21 + JPA + MySQL 8 + Server酱推送
- **前端**：Vue 3 + Vite 5 + TypeScript + TailwindCSS 3.4.17 + lucide-vue-next
- 单进程 fat jar，前端构建产物直接内嵌后端 `src/main/resources/static`

## 快速开始（开发）

```bash
# 1. 启动 MySQL 并执行 schema.sql + seed-data.sql（建库 rent_system）
# 2. 后端（默认读取 application-local.yml，含本地敏感配置）
cd rent-system-java
./mvnw spring-boot:run

# 3. 前端（另一终端，开发热更新）
cd rent-system-java/frontend
npm install
npm run dev
```

访问 `http://localhost:8080`（生产由后端同源托管前端；开发期 Vite 默认 5173）。

## 打包部署

> 📦 **部署包在 [`deploy/`](./deploy/) 文件夹**：部署说明、SQL 脚本、启动脚本、配置模板、干净 jar 全部集中于此，整个文件夹拷到部署机即可。
> 📘 完整步骤（环境安装、SQL 执行、配置、验证、排障）见 [deploy/DEPLOY.md](./deploy/DEPLOY.md)

```bash
cd rent-system-java/frontend && npm install && npm run build       # 产物写入 src/main/resources/static
cd rent-system-java && ./mvnw -Pdeploy -DskipTests clean package   # -Pdeploy 排除开发敏感配置
cp target/rent-system-java-*.jar deploy/                           # 同步干净 jar 到部署包
java -jar target/rent-system-java-*.jar                            # 或 Windows 双击 start.bat
```

⚠️ 部署打包**必须带 `-Pdeploy`**：默认 `package` 会把 `application-local.yml`（含开发期真实 SendKey、`mock: true`）打进 jar，且 `application.yml` 默认激活 `local`，会导致部署机推送变成假推送并外泄密钥。

## 部署机清单（Windows 华硕天选2）

1. 把整个 `deploy/` 文件夹拷到部署机（如 `D:\rent-system\`）
2. 安装 **JDK 21**（Temurin/Adoptium）与 **MySQL 8**
3. 依次执行 `deploy/schema.sql`、`deploy/seed-data.sql`（脚本自带建库）
4. 复制 `application-prod.yml.example` 为 `application-prod.yml` 并填写：MySQL 密码、`security.secret`、各成员真实 SendKey、`notify.mock: false`
5. 双击 `start.bat` 启动（已默认 `SPRING_PROFILES_ACTIVE=prod`）；防火墙放行 8080
6. 详见 [deploy/DEPLOY.md](./deploy/DEPLOY.md)

## 核心功能

- 房源双状态（空置⇄已租）、软删除、小区/楼栋级联筛选
- 登记出租、两阶段违约退租（没收押金）、续租（旧合同归档+新合同）
- 财产收入按合同「月租÷30×有效天数」分段累计、没收押金独立统计
- 月历热力、未来 7 天临期、每日聚合日报（可编辑 5 变量模板）
- 每小时整点按成员各自时间推送 Server酱，失败降级企业微信，通知可补发

## 演示清单（14 项）

登录 · 总览指标卡 · 财产卡年月切换 · 今日日报展开/复制 · 状态筛选与级联 · 房源详情抽屉 · 登记出租 · 违约两阶段弹窗 · 续租 · 月历热力/点日期 · 临期列表 · 通知记录与补发 · 推送配置(开关+时间) · 模板编辑实时预览/恢复默认 · 新增/编辑/删除房源。
