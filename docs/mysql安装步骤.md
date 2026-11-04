# MySQL 8 本地安装步骤

## 1. 下载

**官方下载页**：https://dev.mysql.com/downloads/mysql/

- 选 **Windows (x86, 64-bit), MSI Installer**
- 选较大的那个（约 300 MB），不要选 "web community"（网络安装器容易失败）

**直链**（8.0.40 当前版）：
https://dev.mysql.com/get/Downloads/MySQL-8.0/mysql-8.0.40-winx64.zip

或用 MSI：
https://dev.mysql.com/get/Downloads/MySQL-8.0/mysql-8.0.40-winx64.msi

## 2. 安装

**MSI 安装器**：
1. 运行 MSI，选 "Custom" 安装
2. 只勾选 "MySQL Server 8.0.x"
3. **重要**：Root 密码设为 `root`（与 `backend/src/main/resources/application.yml` 一致）
4. 端口保持默认 `3306`
5. 选 "Standard System Account"（服务账户）
6. 勾选 "Start MySQL Server at System Startup"

**ZIP 免安装版**（更干净）：
1. 解压到 `D:\tools\mysql`
2. 在 `D:\tools\mysql` 创建 `my.ini`：

```ini
[mysqld]
basedir=D:/tools/mysql
datadir=D:/tools/mysql/data
port=3306
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
default-authentication-plugin=caching_sha2_password
```

3. 初始化数据目录（**以管理员身份**运行）：
```powershell
cd D:\tools\mysql\bin
.\mysqld --initialize-insecure --console
```
（`--insecure` 表示 root 无密码，后续改）

4. 安装为 Windows 服务：
```powershell
.\mysqld --install MySQL80
net start MySQL80
```

5. 设置 root 密码为 `root`：
```powershell
.\mysql -u root
# 在 mysql 提示符中：
ALTER USER 'root'@'localhost' IDENTIFIED BY 'root';
FLUSH PRIVILEGES;
EXIT;
```

## 3. 创建数据库

```powershell
cd D:\tools\mysql\bin
.\mysql -u root -proot
```

```sql
CREATE DATABASE IF NOT EXISTS movie_sentiment
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
EXIT;
```

## 4. 验证

```powershell
# 检查服务
Get-Service MySQL80

# 测试连接
cd D:\tools\mysql\bin
.\mysql -u root -proot -e "SELECT VERSION(); SHOW DATABASES;"
```

期望输出包含 `movie_sentiment` 数据库。

## 5. 配置环境变量（可选但推荐）

把 `D:\tools\mysql\bin` 加入 PATH，方便直接用 `mysql` 命令。

## 常见问题

**"Access denied for user 'root'@'localhost'"**
→ 密码不对。用 `--initialize-insecure` 重置或重装。

**"Can't connect to MySQL server on 'localhost:3306'"**
→ 服务没启动：`net start MySQL80`

**端口被占用**
→ 改 `my.ini` 的 `port=3307`，并同步改 `backend/src/main/resources/application.yml` 的 URL。

## 装完后

```powershell
cd "D:\Program Files\Projects\personal\movieCommentAnalysis"
cd backend
mvn spring-boot:run
# 期望：Tomcat started on port 8081，无数据源错误

# 另一个终端
curl http://localhost:8081/actuator/health
# 期望：{"status":"UP"}
```
