package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"logViewer/internal/svc"
	"logViewer/internal/types"
	"net/http"
	"time"

	"github.com/zeromicro/go-zero/rest/httpx"
)

func NewSearchLogsHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req types.QueryParam
		if err := httpx.Parse(r, &req); err != nil {
			httpx.Error(w, err)
			return
		}

		// 构造 bool 查询
		boolQ := map[string]interface{}{
			"bool": map[string]interface{}{
				"filter": []interface{}{},
			},
		}
		filters := boolQ["bool"].(map[string]interface{})["filter"].([]interface{})

		if len(req.Messages) > 0 {
			msgBool := BuildMessageBool(req.Messages)
			boolRoot := boolQ["bool"].(map[string]interface{})
			if len(msgBool["must"]) > 0 {
				boolRoot["must"] = msgBool["must"]
			}
			if len(msgBool["should"]) > 0 {
				boolRoot["should"] = msgBool["should"]
				boolRoot["minimum_should_match"] = 1
			}
			if len(msgBool["must_not"]) > 0 {
				boolRoot["must_not"] = msgBool["must_not"]
			}
		}

		if req.Level != "" {
			filters = append(filters, map[string]interface{}{
				"term": map[string]interface{}{"level": req.Level},
			})
		}
		if req.App != "" {
			filters = append(filters, map[string]interface{}{
				"term": map[string]interface{}{"app": req.App},
			})
		}

		if req.TraceID != "" {
			filters = append(filters, map[string]interface{}{
				"term": map[string]interface{}{"trace_id": req.TraceID},
			})
		}

		if req.Env != "" {
			filters = append(filters, map[string]interface{}{
				"term": map[string]interface{}{"env": req.Env},
			})
		}

		// 时间范围
		start, _ := parseTime(req.Start)
		end, _ := parseTime(req.End)
		if !start.IsZero() || !end.IsZero() {
			rangeQ := map[string]interface{}{}
			if !start.IsZero() {
				rangeQ["gte"] = start.Format(time.RFC3339)
			}
			if !end.IsZero() {
				rangeQ["lte"] = end.Format(time.RFC3339)
			}
			filters = append(filters, map[string]interface{}{
				"range": map[string]interface{}{
					"@timestamp": rangeQ,
				},
			})
		}

		// 分页
		if req.Page <= 0 {
			req.Page = 1
		}
		if req.PageSize <= 0 || req.PageSize > 100 {
			req.PageSize = 15
		}
		from := (req.Page - 1) * req.PageSize

		query := map[string]interface{}{
			"query": boolQ,
			"sort": []interface{}{
				map[string]interface{}{"@timestamp": map[string]string{"order": "desc"}},
			},
			"from": from,
			"size": req.PageSize,
		}

		boolQ["bool"].(map[string]interface{})["filter"] = filters

		queryJSON, _ := json.MarshalIndent(query, "", "  ")
		log.Printf("Elasticsearch Query:\n%s\n", queryJSON)

		var buf bytes.Buffer
		if err := json.NewEncoder(&buf).Encode(query); err != nil {
			httpx.WriteJson(w, http.StatusInternalServerError, types.Resp{Code: 500, Msg: err.Error()})
			return
		}

		logIndexPattern := svcCtx.Config.Elasticsearch.IndexPrefix + "*"

		resp, err := svcCtx.ESClient.Search(
			svcCtx.ESClient.Search.WithContext(context.Background()),
			svcCtx.ESClient.Search.WithIndex(logIndexPattern),
			svcCtx.ESClient.Search.WithBody(&buf),
			svcCtx.ESClient.Search.WithTrackTotalHits(true),
		)
		if err != nil {
			fmt.Println(err)
			httpx.WriteJson(w, http.StatusInternalServerError, types.Resp{Code: 500, Msg: err.Error()})
			return
		}
		defer resp.Body.Close()

		if resp.IsError() {
			fmt.Println(resp)
			httpx.WriteJson(w, http.StatusInternalServerError, types.Resp{Code: 500, Msg: resp.String()})
			return
		}

		var esResp struct {
			Hits struct {
				Total struct {
					Value int64 `json:"value"`
				} `json:"total"`
				Hits []struct {
					Source types.LogDoc `json:"_source"`
				} `json:"hits"`
			} `json:"hits"`
		}
		if err := json.NewDecoder(resp.Body).Decode(&esResp); err != nil {
			httpx.WriteJson(w, http.StatusInternalServerError, types.Resp{Code: 500, Msg: err.Error()})
			return
		}

		logs := make([]types.LogDoc, 0, len(esResp.Hits.Hits))
		for _, h := range esResp.Hits.Hits {
			logs = append(logs, h.Source)
		}

		httpx.WriteJson(w, http.StatusOK, types.LogResponse{
			Code: 0,
			Msg:  "success",
			Data: types.Data{
				Total: esResp.Hits.Total.Value,
				Logs:  logs,
			},
		})
	}
}

// 将字符串时间转为 time.Time
func parseTime(ts string) (time.Time, error) {
	if ts == "" {
		return time.Time{}, nil
	}
	return time.ParseInLocation("2006-01-02 15:04:05", ts, time.Local)
}

// BuildMessageBool 构建消息查询 - 支持基于前一个标签逻辑的组合模糊查询
func BuildMessageBool(messages []types.Tag) map[string][]interface{} {
	result := map[string][]interface{}{
		"must":     {},
		"should":   {},
		"must_not": {},
	}

	if len(messages) == 0 {
		return result
	}

	// 处理组合查询逻辑
	var andConditions []interface{} // 必须同时满足的条件
	var orConditions []interface{}  // 满足其中一个即可的条件
	var notConditions []interface{} // 不能满足的条件

	// 当前处理模式：and、or、not
	currentMode := "and"

	for i := 0; i < len(messages); i++ {
		tag := messages[i]

		// 跳过空值的标签
		if tag.Value == "" {
			continue
		}

		// 创建模糊查询
		wildcardQuery := map[string]interface{}{
			"match_phrase": map[string]interface{}{
				"message": tag.Value,
			},
		}

		// 第一个标签的 op 字段不做逻辑校验，默认作为 and 条件
		if i == 0 {
			andConditions = append(andConditions, wildcardQuery)
			// 设置当前模式为第一个标签的 op（如果存在）
			if tag.Op == "or" || tag.Op == "not" {
				currentMode = tag.Op
			}
			continue
		}

		// 根据前一个标签的 op 字段决定当前标签的逻辑关系
		prevTag := messages[i-1]

		// 如果前一个标签有明确的逻辑操作符，更新当前模式
		if prevTag.Op == "and" || prevTag.Op == "or" || prevTag.Op == "not" {
			currentMode = prevTag.Op
		}

		// 根据当前模式处理查询条件
		switch currentMode {
		case "and":
			andConditions = append(andConditions, wildcardQuery)
		case "or":
			orConditions = append(orConditions, wildcardQuery)
		case "not":
			notConditions = append(notConditions, wildcardQuery)
		default:
			// 默认作为 and 条件
			andConditions = append(andConditions, wildcardQuery)
		}
	}

	// 构建最终的查询结构
	if len(andConditions) > 0 {
		if len(andConditions) == 1 {
			result["must"] = append(result["must"], andConditions[0])
		} else {
			// 多个 and 条件需要同时满足
			result["must"] = append(result["must"], map[string]interface{}{
				"bool": map[string]interface{}{
					"must": andConditions,
				},
			})
		}
	}

	if len(orConditions) > 0 {
		if len(orConditions) == 1 {
			result["should"] = append(result["should"], orConditions[0])
		} else {
			// 多个 or 条件需要满足其中一个
			result["should"] = append(result["should"], map[string]interface{}{
				"bool": map[string]interface{}{
					"should":               orConditions,
					"minimum_should_match": 1,
				},
			})
		}
	}

	if len(notConditions) > 0 {
		if len(notConditions) == 1 {
			result["must_not"] = append(result["must_not"], notConditions[0])
		} else {
			// 多个 not 条件都不能满足
			result["must_not"] = append(result["must_not"], map[string]interface{}{
				"bool": map[string]interface{}{
					"must": notConditions,
				},
			})
		}
	}

	return result
}
