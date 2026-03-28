# data-service 脚本说明

## 当前范围

- `bin/` 当前只维护 `data-service-app` 后端相关脚本。
- 默认 Spring Profile 为 `local`，生产模式使用 `prod`。
- 当前仓库没有可直接启动的前端工程，旧的前端启动/停止逻辑已移除。
- 当前 [DataServiceApplication.java](/Volumes/osdisk/java_code/data-service/data-service-app/src/main/java/cn/dtkeys/dataservice/app/DataServiceApplication.java) 还没有补齐 Spring Boot 启动入口；若启动失败，先完善该类。

## 前置条件

- JDK 17+
- Maven 3.9+

## 启动与停止

```bash
# 本地模式（默认）
./bin/start.sh

# 显式指定本地模式
./bin/start.sh --local

# 兼容旧参数，等同 --local
./bin/start.sh --dev

# 生产模式
./bin/start.sh --prod

# 停止后端
./bin/stop.sh

# 重启后端
./bin/restart.sh
./bin/restart.sh --prod
```

说明：
- `./bin/dev-start.sh`：使用 `mvn -pl data-service-app -am spring-boot:run` 直接启动本地环境。
- `./bin/start-app.sh`：先打包 `data-service-app`，再以 `java -jar` 启动 `local` profile。
- 默认端口按应用配置执行；当前未显式配置时，Spring Boot 默认使用 `8080`。

## 日志

- `logs/backend.log`：后端 stdout/stderr

## 生产模式构建

```bash
mvn -pl data-service-app -am package -DskipTests
```

产物路径：

```bash
data-service-app/target/data-service-app-*.jar
```

## Git 辅助脚本

```bash
# 审阅摘要 + pre-commit 检查
./bin/git-check.sh

# 附加执行 pre-push gate
./bin/git-check.sh --push-gate

# 仅查看审阅摘要
./bin/git-check.sh --review-only

# 查看推荐提交流程
./bin/git-step-0-sequence.sh

# 一键串联 review -> stage -> check -> commit -> push
./bin/git-step-all.sh <path...>

# 非交互式串联
./bin/git-step-all-auto.sh <path...>
./bin/git-step-all-auto.sh --auto-push <path...>
./bin/git-step-all-auto.sh --auto-push --skip-tests <path...>
```

首次启用 hooks：

```bash
git config core.hooksPath .githooks
chmod +x .githooks/* bin/*.sh bin/lib/*.sh
```

说明：
- `git-step-3b-draft-msg.sh` 已按 `data-service-*` 模块生成 scope。
- `git-check.sh` 不再依赖旧项目的 `scripts/git-review.sh`。
- 如果仓库中尚未提供 `.githooks/pre-commit` 或 `.githooks/pre-push`，脚本会提示并跳过对应 gate。
