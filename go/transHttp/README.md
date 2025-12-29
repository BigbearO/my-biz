# HTTP 转发代理服务器

这是一个基于Go的HTTP转发代理服务器，可以根据不同的路径前缀将请求转发到不同的目标服务器。

## 功能特点

- 基于路径前缀的HTTP请求转发
- 可配置的转发规则
- 支持多个目标服务器
- 保持原始请求头信息
- 添加转发相关的头信息（X-Forwarded-*）

## 项目结构

```
transHttp/
├── go.mod          # Go模块依赖
├── main.go         # 主程序入口
├── config.go       # 配置文件处理
├── proxy.go        # HTTP代理转发逻辑
├── config.json     # 转发规则配置
└── README.md       # 项目说明
```

## 安装和运行

### 1. 安装依赖
```bash
go mod download
```

### 2. 配置转发规则
编辑 `config.json` 文件，配置你的转发规则：

```json
{
  "port": 8080,
  "routes": [
    {
      "path_prefix": "/api/v1",
      "target_url": "http://localhost:3001"
    },
    {
      "path_prefix": "/api/v2", 
      "target_url": "http://localhost:3002"
    },
    {
      "path_prefix": "/web",
      "target_url": "http://localhost:3003"
    }
  ]
}
```

### 3. 启动服务器
```bash
go run .
```

服务器将在配置的端口上启动，并显示配置的转发规则。

## 使用示例

假设你的配置文件如下：

```json
{
  "port": 8080,
  "routes": [
    {
      "path_prefix": "/api",
      "target_url": "http://localhost:3001"
    },
        {
          "path_prefix": "/back",
          "target_url": "http://192.168.4.78:9999"
        },
    {
      "path_prefix": "/web",
      "target_url": "http://localhost:3002"
    }
  ]
}
```

那么：

- 访问 `http://localhost:8080/api/users` 会被转发到 `http://localhost:3001/users`
- 访问 `http://localhost:8080/web/index.html` 会被转发到 `http://localhost:3002/index.html`
- 访问 `http://localhost:8080/other` 会返回404错误（没有匹配的路由）

## 配置说明

### config.json 字段说明

- `port`: 代理服务器监听的端口号
- `routes`: 转发规则数组，每个规则包含：
  - `path_prefix`: 路径前缀，用于匹配请求URL
  - `target_url`: 目标服务器地址

### 转发规则匹配

- 转发规则按照配置顺序进行匹配
- 使用最长前缀匹配原则
- 匹配到的路径前缀会从原始URL中移除，剩余部分添加到目标URL后面

## 开发说明

### 主要组件

- **Config**: 配置管理，负责加载和解析配置文件
- **ProxyHandler**: HTTP请求处理器，实现转发逻辑
- **main**: 程序入口，负责初始化和启动HTTP服务器

### 扩展功能

你可以根据需要扩展以下功能：

- 支持HTTPS转发
- 添加请求/响应日志
- 实现负载均衡
- 添加认证和授权
- 支持WebSocket转发
- 添加请求限流

## 许可证

MIT License