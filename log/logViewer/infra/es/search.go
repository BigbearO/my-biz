package es

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"

	elasticsearch "github.com/elastic/go-elasticsearch/v8"
)

// ExecuteSearch 执行 ES 查询，返回日志列表和总数
func ExecuteSearch(ctx context.Context, client *elasticsearch.Client, query map[string]interface{}, indexPattern string) ([]LogDoc, int64, error) {
	var buf bytes.Buffer
	if err := json.NewEncoder(&buf).Encode(query); err != nil {
		return nil, 0, fmt.Errorf("encode query error: %w", err)
	}

	log.Printf("Elasticsearch Query:\n%s\n", queryJSONPretty(query))

	resp, err := client.Search(
		client.Search.WithContext(ctx),
		client.Search.WithIndex(indexPattern),
		client.Search.WithBody(&buf),
		client.Search.WithTrackTotalHits(true),
	)
	if err != nil {
		return nil, 0, fmt.Errorf("execute search error: %w", err)
	}
	defer resp.Body.Close()

	if resp.IsError() {
		return nil, 0, fmt.Errorf("search response error: %s", resp.String())
	}

	var esResp struct {
		Hits struct {
			Total struct {
				Value int `json:"value"`
			} `json:"total"`
			Hits []struct {
				Source LogDoc `json:"_source"`
			} `json:"hits"`
		} `json:"hits"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&esResp); err != nil {
		return nil, 0, fmt.Errorf("decode response error: %w", err)
	}

	logs := make([]LogDoc, 0, len(esResp.Hits.Hits))
	for _, h := range esResp.Hits.Hits {
		logs = append(logs, h.Source)
	}

	return logs, int64(esResp.Hits.Total.Value), nil
}

// queryJSONPretty 将查询 JSON 格式化输出（用于调试）
func queryJSONPretty(query map[string]interface{}) string {
	data, _ := json.MarshalIndent(query, "", "  ")
	return string(data)
}
