# bin 目录说明

`bin/` 存放 `data-service` 的本地启动、停止、重启、联邦模块测试和 Git 辅助脚本。

## 1. 目录内容

| 路径 | 作用 |
| --- | --- |
| `bin/start.sh` | 启动 `data-service-app`，支持 `local` / `prod` 模式 |
| `bin/stop.sh` | 停止占用 `8081` 端口的后端进程 |
| `bin/restart.sh` | 先停再启，复用 `start.sh` / `stop.sh` 参数 |
| `bin/start-app.sh` | 清理并打包 `data-service-app`，以前台方式启动 |
| `bin/test-federation.sh` | 清理并执行 `data-service-federation` 模块指定测试 |
| `bin/git/` | Git 提交、校验、推送辅助脚本，详见 `bin/git/README.md` |

## 2. 前置条件

| 项目 | 要求 |
| --- | --- |
| Java | JDK 21+ |
| Maven | Maven 3.9+ |
| 数据库 | 本地 PostgreSQL 已就绪，`data-service-app` 可正常连接 |
| 端口 | 本地 `8081` 未被其他进程占用 |

## 3. 启动与停止

### 3.1 `start.sh`

```bash
# 本地模式（默认）
./bin/start.sh

# 显式指定本地模式
./bin/start.sh --local
./bin/start.sh --dev

# 生产模式：先 package，再 java -jar 启动
./bin/start.sh --prod

# 兼容参数，当前项目仅维护后端进程
./bin/start.sh --backend-only
```

说明：

| 项目 | 内容 |
| --- | --- |
| 本地模式 | 执行 `mvn -f data-service-app/pom.xml spring-boot:run`，使用 `local` profile |
| 生产模式 | 执行 `mvn -pl data-service-app -am package -DskipTests`，再启动 JAR |
| 日志 | 后端输出写入 `logs/backend.log` |
| 就绪判断 | 轮询 `8081` 端口，最长等待 30 秒 |
| 不支持项 | `--frontend-only` 会直接失败，因为当前项目没有由该脚本管理的前端进程 |

### 3.2 `stop.sh`

```bash
./bin/stop.sh
./bin/stop.sh --backend-only
```

说明：

| 项目 | 内容 |
| --- | --- |
| 停止范围 | 强制停止占用 `8081` 端口的进程 |
| 不支持项 | `--frontend-only` 会直接失败 |

### 3.3 `restart.sh`

```bash
./bin/restart.sh
./bin/restart.sh --prod
./bin/restart.sh --backend-only
```

说明：

| 项目 | 内容 |
| --- | --- |
| 行为 | 先执行 `stop.sh`，等待 2 秒，再执行 `start.sh` |
| 参数 | 透传给 `stop.sh` 和 `start.sh` |

### 3.4 `start-app.sh`

```bash
./bin/start-app.sh

# 追加 Spring Boot 参数
./bin/start-app.sh --server.port=8081
```

说明：

| 项目 | 内容 |
| --- | --- |
| 构建方式 | 先 `clean`，再 `package -DskipTests` |
| 启动方式 | 以前台方式执行 `java -jar` |
| 默认 profile | `local` |
| 适用场景 | 本地手工调试，需要看到实时控制台输出时使用 |

## 4. 联邦模块测试

### 4.1 `test-federation.sh`

```bash
./bin/test-federation.sh
```

说明：

| 项目 | 内容 |
| --- | --- |
| 清理范围 | 删除 `data-service-federation/target` |
| 执行测试 | `PredefinedJoinQueryExecutorTest`、`FederatedPipelineTest` |
| Maven 命令 | `mvn -pl data-service-federation -am test ...` |
| 适用场景 | 联邦执行器、预定义 Join、联邦管线改动后的定向验证 |

## 5. Git 辅助脚本

常用入口：

```bash
./bin/git/git-guide.sh
./bin/git/git-review.sh
./bin/git/git-stage.sh <path...>
./bin/git/git-prepare-commit.sh
./bin/git/git-commit-staged.sh --from-draft
./bin/git/git-push-safe.sh --remote origin --branch <branch>
```

完整说明见：

| 文档 | 作用 |
| --- | --- |
| `bin/git/README.md` | Git 脚本用途、依赖关系、推荐流程 |

## 6. 运行文件与日志

| 路径 | 作用 |
| --- | --- |
| `logs/backend.log` | `start.sh` 启动的后端日志 |
| `data-service-app/target/data-service-app-*.jar` | `--prod` 或 `start-app.sh` 生成的可执行包 |

## 7. 注意事项

| 项目 | 说明 |
| --- | --- |
| 项目定位 | 当前 `bin` 目录只维护后端运行脚本，不再描述旧项目的前后端双进程模式 |
| 项目名称 | 一律使用 `data-service`，不再使用历史名称 `LinkInsight` |
| 脚本边界 | `bin/README.md` 只说明 `bin` 目录现有脚本，不记录通用优化方案或与当前仓库无关的流程 |
