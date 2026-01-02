package application

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"logViewer/infra/es"
	"time"

	"github.com/elastic/go-elasticsearch/v8"
	"github.com/elastic/go-elasticsearch/v8/esapi"
)

// 模拟写入几条日志（仅演示）
func InsertDemo(client *elasticsearch.Client) {
	docs := []es.LogDoc{
		{
			Timestamp: time.Date(2025, 9, 26, 14, 10, 0, 0, time.UTC),
			Level:     "info",
			Message:   "user login succeeded",
			//Service:   "auth",
			TraceID: "a1b2c3d4e5f6",
			App:     "gateway",
			Env:     "prod",
		},
		{
			Timestamp: time.Date(2025, 9, 26, 14, 9, 0, 0, time.UTC),
			Level:     "debug",
			Message:   "cache hit for product:sku123",
			//Service:   "catalog",
			TraceID: "a1b2c3d4e5f6",
			App:     "shop",
			Env:     "staging",
		},
		{
			Timestamp: time.Date(2025, 9, 26, 14, 8, 0, 0, time.UTC),
			Level:     "warn",
			Message:   "slow query took 1.2s",
			//Service:   "order",
			TraceID: "c3d4e5f6g7h8",
			App:     "backend",
			Env:     "prod",
		},
		{
			Timestamp: time.Date(2025, 9, 26, 16, 7, 0, 0, time.UTC),
			Level:     "error",
			Message:   "payment provider timeout",
			//Service:   "payment",
			TraceID: "a1b2c3d4e5f6",
			App:     "checkout",
			Env:     "prod",
		},
		{
			Timestamp: time.Date(2025, 9, 26, 14, 6, 0, 0, time.UTC),
			Level:     "info",
			Message:   "order #1001 created",
			//Service:   "order",
			TraceID: "e5f6g7h8i9j0",
			App:     "backend",
			Env:     "prod",
		},
		{
			Timestamp: time.Date(2025, 9, 26, 14, 5, 0, 0, time.UTC),
			Level:     "debug",
			Message:   "received webhook from stripe",
			//Service:   "payment",
			TraceID: "f6g7h8i9j0k1",
			App:     "checkout",
			Env:     "staging",
		},
		{
			Timestamp: time.Date(2025, 9, 26, 17, 4, 0, 0, time.UTC),
			Level:     "warn",
			Message:   "invalid jwt token",
			//Service:   "auth",
			TraceID: "g7h8i9j0k1l2",
			App:     "gateway",
			Env:     "prod",
		},
		{
			Timestamp: time.Date(2025, 9, 26, 17, 3, 0, 0, time.UTC),
			Level:     "error",
			Message:   "sku not found in inventory",
			//Service:   "inventory",
			TraceID: "h8i9j0k1l2m3",
			App:     "shop",
			Env:     "staging",
		},
		{
			Timestamp: time.Date(2025, 9, 26, 17, 2, 0, 0, time.UTC),
			Level:     "info",
			Message:   "shipment #200 dispatched",
			//Service:   "logistics",
			TraceID: "i9j0k1l2m3n4",
			App:     "tracker",
			Env:     "prod",
		},
		{
			Timestamp: time.Date(2025, 9, 26, 17, 1, 0, 0, time.UTC),
			Level:     "debug",
			Message:   "health check ok",
			//Service:   "monitor",
			TraceID: "j0k1l2m3n4o5",
			App:     "ops",
			Env:     "staging",
		},
	}

	for _, doc := range docs {
		body, _ := json.Marshal(doc)
		req := esapi.IndexRequest{
			Index:      fmt.Sprintf("log-%s", doc.Timestamp.Format("2006-01-02")),
			DocumentID: "", // 让 ES 自动生成
			Body:       bytes.NewReader(body),
			Refresh:    "true",
		}
		if _, err := req.Do(context.Background(), client); err != nil {
			log.Printf("insert demo err: %v", err)
		}
	}
}
