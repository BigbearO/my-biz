package generate

import (
	"fmt"
	"os"
	"strings"

	"github.com/AlecAivazis/survey/v2"
	"github.com/spf13/cobra"

	"github.com/wind-scaffold/wind/internal/pkg"
	"github.com/wind-scaffold/wind/pkg/generators"
	sqlkratos "github.com/wind-scaffold/wind/pkg/sqlkratos"
)

// CmdGenerate represents the generate command
var CmdGenerate = &cobra.Command{
	Use:          "generate",
	Aliases:      []string{"gen"},
	Short:        "generate CRUD code from database schema",
	Long:         "Generate complete Kratos microservice code (proto, ORM, service, server, wiring, config) from an existing database, SQL file, or Go schema source (ent schema dir via ent://, gorm model dir via gorm://). Module registration follows the target service form: anchor injection into hand-written wiring.go, or wire provider sets for legacy services. Example: gow generate",
	RunE:         Run,
	SilenceUsage: true,
}

var (
	genDSN           string
	genDriver        string
	genServiceName   string
	genOrmType       string
	genServers       []string
	genTables        []string
	genExcludeTables []string
	genModuleVersion string
	genProtoOnly     bool
	genSkipORM       bool
	genSkipConfig    bool
	genSkipMakefile  bool
	genSourceModule  string
	genDryRun        bool
	genVerify        bool
)

func init() {
	CmdGenerate.Flags().StringVarP(&genDSN, "dsn", "", "", "Database source name (DSN), e.g. mysql://user:pass@tcp(localhost:3306)/dbname; Go schema sources: ent://<schema dir>, gorm://<model dir>")
	CmdGenerate.Flags().StringVarP(&genDriver, "driver", "", "mysql", "Database driver: mysql, postgres")
	CmdGenerate.Flags().StringVarP(&genServiceName, "service", "", "", "Service name (module name)")
	CmdGenerate.Flags().StringVarP(&genOrmType, "orm", "", "ent", "ORM type: ent, gorm")
	CmdGenerate.Flags().StringArrayVarP(&genServers, "servers", "s", []string{"grpc"}, "Server types: grpc, rest(v3 谱系不支持其他传输)")
	CmdGenerate.Flags().StringArrayVarP(&genTables, "tables", "t", nil, "Tables to include (default: all tables)")
	CmdGenerate.Flags().StringArrayVarP(&genExcludeTables, "exclude-tables", "", nil, "Tables to exclude")
	CmdGenerate.Flags().StringVarP(&genModuleVersion, "module-version", "", "v1", "API module version")
	CmdGenerate.Flags().BoolVarP(&genProtoOnly, "proto-only", "", false, "Only generate proto files")
	CmdGenerate.Flags().BoolVarP(&genSkipORM, "skip-orm", "", false, "Skip ORM code generation")
	CmdGenerate.Flags().BoolVarP(&genSkipConfig, "skip-config", "", false, "Skip config file generation")
	CmdGenerate.Flags().BoolVarP(&genSkipMakefile, "skip-makefile", "", false, "Skip Makefile generation")
	CmdGenerate.Flags().StringVarP(&genSourceModule, "source-module", "", "", "Source module name for REST service")
	CmdGenerate.Flags().BoolVarP(&genDryRun, "dry-run", "n", false, "Validate the data source, resolve tables and preview the plan without writing anything")
	CmdGenerate.Flags().BoolVar(&genVerify, "verify", true, "生成后执行 gofmt/buf/build 校验,任一步失败回滚本次生成(false 跳过校验)")
}

func Run(cmd *cobra.Command, args []string) error {
	// 交互式获取缺失参数
	if genDSN == "" {
		prompt := &survey.Input{
			Message: "Database DSN?",
			Help:    "Database connection string (e.g. mysql://user:pass@tcp(localhost:3306)/dbname), or a Go schema source: ent://<schema dir> / gorm://<model dir>",
		}
		if err := survey.AskOne(prompt, &genDSN); err != nil || genDSN == "" {
			return nil
		}
	}

	if genServiceName == "" {
		prompt := &survey.Input{
			Message: "Service name?",
			Help:    "The service/module name to generate code for.",
		}
		if err := survey.AskOne(prompt, &genServiceName); err != nil || genServiceName == "" {
			return nil
		}
	}

	// 获取项目信息
	inspector, err := pkg.NewModuleInspectorFromGo(cmd.Context(), "")
	if err != nil {
		return err
	}

	// 生成代码的 import 前缀必须用完整 module 路径(gow 原版取末段,对带域名的 module 会生成错误 import)
	projectName := inspector.ModPath
	outputPath := inspector.Root

	sourceModule := genSourceModule
	if sourceModule == "" {
		sourceModule = genServiceName
	}

	// 构建 DSN 前缀（如果用户没有提供 scheme）
	dsn := genDSN

	// 裸目录路径多半是想用 Go 源码 schema 源,给出明确指引。
	if !strings.Contains(dsn, "://") {
		if fi, statErr := os.Stat(dsn); statErr == nil && fi.IsDir() {
			return fmt.Errorf("source %q is a directory; use ent://<dir> (ent schema dir) or gorm://<dir> (gorm model dir)", dsn)
		}
	}

	// Go 源码 schema 源与 ORM 选择必须一致(dry-run 也要能发现)。
	switch {
	case strings.HasPrefix(dsn, "ent://") && genOrmType != "ent":
		return fmt.Errorf("source ent:// requires --orm ent (got %q)", genOrmType)
	case strings.HasPrefix(dsn, "gorm://") && genOrmType != "gorm":
		return fmt.Errorf("source gorm:// requires --orm gorm (got %q)", genOrmType)
	}

	// 第一期仅支持:SQL 文件/内联 DDL(file:// text:// 裸路径)与 ent://;
	// 活库 introspect(mysql:// postgres://)与 gorm:// 为后置能力,显式报错退出(不静默降级)。
	switch {
	case strings.HasPrefix(dsn, "mysql://"), strings.HasPrefix(dsn, "postgres://"):
		return fmt.Errorf("活库 introspect(%q)为后置能力,第一期请使用 SQL 文件(file://<path> 或内联 DDL text://)", strings.SplitN(dsn, ":", 2)[0])
	case strings.HasPrefix(dsn, "gorm://"):
		return fmt.Errorf("gorm:// 源为后置能力,第一期请使用 SQL 文件或 ent://<schema目录>")
	}

	// 逗号分隔写法(-s grpc,rest)与重复 flag 写法(-s grpc -s rest)等价。
	genServers = pkg.SplitFlagList(genServers)
	if err := generators.ValidateServers(genServers); err != nil {
		return err
	}

	// 纯 rest(BFF 网关)依赖「源 grpc 服务」的 proto(--source-module 指定);
	// BFF 完整链为后置能力,第一期要求后端服务带 grpc,或显式给 --source-module。
	hasGrpc := false
	for _, s := range genServers {
		if strings.EqualFold(strings.TrimSpace(s), "grpc") {
			hasGrpc = true
			break
		}
	}
	if !hasGrpc && genSourceModule == "" {
		return fmt.Errorf("纯 rest 服务为 BFF 网关形态,需用 --source-module 指向已生成的源 grpc 服务模块(如 user);或改为 -s grpc -s rest 双 server 的常规后端服务形态(BFF 完整链为后置能力)")
	}

	opts := sqlkratos.GeneratorOptions{
		Driver:           genDriver,
		Source:           dsn,
		IncludedTables:   genTables,
		ExcludedTables:   genExcludeTables,
		OutputPath:       outputPath,
		SourceModuleName: sourceModule,
		ModuleName:       genServiceName,
		ModuleVersion:    genModuleVersion,
		OrmType:          genOrmType,
		ProjectName:      projectName,
		ServiceName:      genServiceName,
		Servers:          genServers,
		UseRepo:          true,
		GenerateProto:    true,
		GenerateServer:   !genProtoOnly,
		GenerateService:  !genProtoOnly,
		GenerateORM:      !genProtoOnly && !genSkipORM,
		GenerateData:     !genProtoOnly,
		GenerateMain:     !genProtoOnly,
		GenerateConfig:   !genProtoOnly && !genSkipConfig,
		GenerateMakefile: !genProtoOnly && !genSkipMakefile,
	}

	fmt.Printf("Generating code for service [%s] with ORM [%s]...\n", genServiceName, genOrmType)

	if genDryRun {
		tables, terr := sqlkratos.PlanTables(cmd.Context(), opts)
		if terr != nil {
			return fmt.Errorf("resolve tables: %w", terr)
		}
		fmt.Printf("Plan for service [%s]:\n", genServiceName)
		fmt.Printf("  Source:      %s (driver: %s)\n", dsn, genDriver)
		fmt.Printf("  ORM:         %s\n", genOrmType)
		fmt.Printf("  Servers:     %s\n", strings.Join(genServers, ", "))
		fmt.Printf("  API version: %s\n", genModuleVersion)
		fmt.Printf("  Output:      %s\n", outputPath)
		var skips []string
		if genProtoOnly {
			skips = append(skips, "proto-only (no server/service/ORM/config/Makefile)")
		}
		if genSkipORM {
			skips = append(skips, "ORM")
		}
		if genSkipConfig {
			skips = append(skips, "config")
		}
		if genSkipMakefile {
			skips = append(skips, "Makefile")
		}
		if len(skips) > 0 {
			fmt.Printf("  Skipped:     %s\n", strings.Join(skips, ", "))
		}
		fmt.Printf("  Resolved %d table(s):\n", len(tables))
		for _, t := range tables {
			note := ""
			if len(t.Fields) == 0 {
				note = "  (no fields — will be skipped)"
			}
			fmt.Printf("    - %s (%d field(s))%s\n", t.Name, len(t.Fields), note)
		}
		fmt.Printf("\033[36m[DRY-RUN] preview only — nothing was written. Re-run without --dry-run to execute.\033[m\n")
		return nil
	}

	// 快照必须在生成之前——回滚才真正覆盖本次生成的文件(此前快照在生成后拍,回滚形同虚设)。
	roots := affectedRoots(outputPath, genServiceName, genProtoOnly)
	snap, snapErr := snapshotDirs(roots)
	if snapErr != nil && genVerify {
		return fmt.Errorf("生成前快照失败: %w", snapErr)
	}

	if err := sqlkratos.Generate(cmd.Context(), opts); err != nil {
		if genVerify {
			restoreDirs(roots, snap)
		}
		return err
	}

	// 生成链收口:gofmt → buf → tidy → build;任一步失败回滚本次生成文件。
	if genVerify {
		if verr := verifyChain(cmd.Context(), outputPath, genServiceName, genOrmType); verr != nil {
			restoreDirs(roots, snap)
			return fmt.Errorf("校验失败,已回滚本次生成的全部文件:\n%v", verr)
		}
	} else {
		fmt.Println("⚠️  --verify=false:未执行 gofmt/buf/build 校验,生成物未经编译验证")
	}

	fmt.Printf("\033[32mService [%s] generated successfully!\033[m\n", genServiceName)
	return nil
}

func extractProjectName(module string) string {
	if module == "" {
		return ""
	}
	if idx := len(module) - 1; idx >= 0 {
		for i := len(module) - 1; i >= 0; i-- {
			if module[i] == '/' {
				return module[i+1:]
			}
		}
	}
	return module
}
