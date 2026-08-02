package data

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"path/filepath"

	"github.com/go-kratos/kratos/v2/log"
	"github.com/tx7do/kratos-bootstrap/bootstrap"

	_ "github.com/jackc/pgx/v5/stdlib"

	"bigbear.com/xxs-common/xxs-otel-log/logx"

	"seata.apache.org/seata-go/v2/pkg/client"
)

// seataGoConfig 默认 seata-go 配置文件路径（相对进程 CWD，可被 SEATA_CONF 环境变量覆盖）。
const seataGoConfig = "conf/seatago.yml"

// userRepoDSN 业务库（seata_go）连接串：由 seata-xa-postgres 驱动使用。
// 部署到其他环境时请通过环境变量 SEATA_DSN 覆盖。
const userRepoDSN = "host=192.168.50.45 port=5432 user=root password=123456 dbname=seata_go sslmode=disable"

// UserRepo 用户仓储：seata 开启时使用 XA 代理连接，关闭时退化为普通 pgx 连接。
//
// 连接被 XA 代理包裹时，全局事务下 ExecContext 自动注册分支；物理连接收敛为 1，
// 保证 PREPARE/COMMIT 在同一个连接上执行。
type UserRepo struct {
	db        *sql.DB
	log       *log.Helper
	xaEnabled bool
}

// NewUserRepo 按 seatago.yml 的 seata.enabled 开关初始化数据源。
//
// 开启：初始化 seata-go 客户端并使用 seata-xa-postgres 驱动；
// 关闭：不初始化 seata，使用普通 pgx 驱动（CreateUser 退化为本地事务）。
//
// 参数 / Parameters:
//   - ctx: 引导上下文
//
// 返回 / Returns:
//   - *UserRepo: 用户仓储实例
//   - func(): 关闭连接池的清理函数
//   - error: 初始化失败（seata 配置加载 / 驱动注册 / 数据库连通性）
func NewUserRepo(ctx *bootstrap.Context) (*UserRepo, func(), error) {
	configPath := resolveSeataConfigPath()
	xaEnabled := SeataEnabled()

	driverName := "pgx"
	if xaEnabled {
		client.InitPath(configPath)
		driverName = "seata-xa-postgres"
	}

	db, err := sql.Open(driverName, dsn())
	if err != nil {
		return nil, nil, fmt.Errorf("open %s failed: %w", driverName, err)
	}
	// XA 分支需始终在同一物理连接上执行 prepare，收敛连接池
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)

	if err := db.Ping(); err != nil {
		db.Close()
		return nil, nil, fmt.Errorf("ping seata_go failed: %w", err)
	}

	return &UserRepo{
		db:        db,
		log:       logx.NewTracedHelper(ctx.GetLogger(), "ai/user-repo"),
		xaEnabled: xaEnabled,
	}, func() { db.Close() }, nil
}

// Enabled 返回当前是否启用分布式事务（XA）。
func (r *UserRepo) Enabled() bool {
	return r.xaEnabled
}

// CreateUser 在全局事务内写入用户，返回新用户 id。
//
// seata-go XA 驱动在执行返回行集的语句时，会在行集关闭前触发 PREPARE，
// 而 pgx 连接锁在行集关闭前不会释放，导致 RETURNING 查询报 conn busy。
// 故改为显式事务：
//  1. ExecContext 执行无返回行的 INSERT（行集立即排空，连接锁释放）
//  2. 同会话 SELECT lastval() 获取自增 id
//  3. tx.Commit 触发 XA END + XA PREPARE
//
// 参数 / Parameters:
//   - ctx: 携带全局事务 XID 的调用上下文（由 SeataXidMiddleware 注入）
//   - name: 用户名
//
// 返回 / Returns:
//   - int64: 新用户自增 id
//   - error: 写入失败
func (r *UserRepo) CreateUser(ctx context.Context, name string) (int64, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		r.log.WithContext(ctx).Errorf("begin tx failed: %v", err)
		return 0, err
	}
	defer func() { _ = tx.Rollback() }()

	if _, err := tx.ExecContext(ctx, "INSERT INTO user_tbl(name) VALUES($1)", name); err != nil {
		r.log.WithContext(ctx).Errorf("insert user_tbl failed: %v", err)
		return 0, err
	}

	var id int64
	if err := tx.QueryRowContext(ctx, "SELECT lastval()").Scan(&id); err != nil {
		r.log.WithContext(ctx).Errorf("query lastval failed: %v", err)
		return 0, err
	}

	if err := tx.Commit(); err != nil {
		r.log.WithContext(ctx).Errorf("commit tx failed: %v", err)
		return 0, err
	}

	r.log.WithContext(ctx).Infof("user created, id=%d name=%s", id, name)
	return id, nil
}

// resolveSeataConfigPath 解析 seatago.yml 路径：优先环境变量 SEATA_CONF，其次 CWD 下 conf/seatago.yml，
// 最后回退到可执行文件所在目录的 conf/seatago.yml。
func resolveSeataConfigPath() string {
	if p := os.Getenv("SEATA_CONF"); p != "" {
		return p
	}
	if _, err := os.Stat(seataGoConfig); err == nil {
		return seataGoConfig
	}
	if exe, err := os.Executable(); err == nil {
		p := filepath.Join(filepath.Dir(exe), "conf", "seatago.yml")
		if _, statErr := os.Stat(p); statErr == nil {
			return p
		}
	}
	return seataGoConfig
}

// dsn 返回业务库连接串，支持环境变量 SEATA_DSN 覆盖。
func dsn() string {
	if p := os.Getenv("SEATA_DSN"); p != "" {
		return p
	}
	return userRepoDSN
}
