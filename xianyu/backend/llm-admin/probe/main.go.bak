package main

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	_ "github.com/jackc/pgx/v5/stdlib"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"

	llmV1 "github.com/xianyu/llm-admin/api/gen/go/llm/service/v1"
)

func main() {
	// 1) pg 行数
	db, err := sql.Open("pgx", "host=10.0.0.5 port=5432 user=root password=123456 dbname=good_fish sslmode=disable")
	if err == nil {
		var n int64
		_ = db.QueryRow(`SELECT count(*) FROM llm_provider`).Scan(&n)
		fmt.Println("PG providers:", n)
		db.Close()
	}

	// 2) 内部 gRPC:GetEnabledModel(直连本机 9000,生产环境走 nacos 发现)
	conn, err := grpc.NewClient("127.0.0.1:9000", grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		fmt.Println("GRPC_ERR:", err)
		return
	}
	defer conn.Close()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	resp, err := llmV1.NewLlmServiceClient(conn).GetEnabledModel(ctx, &llmV1.GetEnabledModelRequest{})
	if err != nil {
		fmt.Println("RPC_ERR:", err)
		return
	}
	fmt.Printf("RPC GetEnabledModel: id=%d provider=%s model=%s baseUrl=%s apiKey=%s temp=%.2f maxTokens=%d\n",
		resp.GetId(), resp.GetProviderName(), resp.GetModelName(), resp.GetBaseUrl(), resp.GetApiKey(), resp.GetTemperature(), resp.GetMaxTokens())
}
