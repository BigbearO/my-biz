package es

import (
	"fmt"
	"strings"
	"time"

	"github.com/spf13/viper"
)

// BuildESQuery 构建 ES 查询，返回完整的查询 map
func BuildESQuery(qp QueryParam, indexPrefix string) (map[string]interface{}, string, error) {
	// 构造 bool 查询
	boolQ := map[string]interface{}{
		"bool": map[string]interface{}{
			"filter": []interface{}{},
		},
	}
	filters := boolQ["bool"].(map[string]interface{})["filter"].([]interface{})

	if qp.Messages != nil && len(qp.Messages) > 0 {
		msgBool := buildMessageBool(qp.Messages)
		if len(msgBool["must"]) > 0 || len(msgBool["should"]) > 0 || len(msgBool["must_not"]) > 0 {
			boolRoot := boolQ["bool"].(map[string]interface{})
			if len(msgBool["must"]) > 0 {
				boolRoot["must"] = msgBool["must"]
			}
			if len(msgBool["should"]) > 0 {
				boolRoot["should"] = msgBool["should"]
				// 默认 should 至少匹配 1 条
				boolRoot["minimum_should_match"] = 1
			}
			if len(msgBool["must_not"]) > 0 {
				boolRoot["must_not"] = msgBool["must_not"]
			}
		}
	}

	if qp.Level != "" {
		filters = append(filters, map[string]interface{}{
			"term": map[string]interface{}{"level": qp.Level},
		})
	}
	if qp.App != "" {
		filters = append(filters, map[string]interface{}{
			"term": map[string]interface{}{"app": qp.App},
		})
	}

	if qp.TraceID != "" {
		filters = append(filters, map[string]interface{}{
			"term": map[string]interface{}{"trace_id": qp.TraceID},
		})
	}

	if qp.Env != "" {
		filters = append(filters, map[string]interface{}{
			"term": map[string]interface{}{"env": qp.Env},
		})
	}

	// 时间范围
	start, err := parseTime(qp.Start)
	if err != nil && qp.Start != "" {
		return nil, "", fmt.Errorf("parse start time error: %w", err)
	}
	end, err := parseTime(qp.End)
	if err != nil && qp.End != "" {
		return nil, "", fmt.Errorf("parse end time error: %w", err)
	}

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
	if qp.Page <= 0 {
		qp.Page = 1
	}
	if qp.PageSize <= 0 || qp.PageSize > 100 {
		qp.PageSize = 15
	}
	from := (qp.Page - 1) * qp.PageSize

	query := map[string]interface{}{
		"query": boolQ,
		"sort": []interface{}{
			map[string]interface{}{"@timestamp": map[string]string{"order": "desc"}},
		},
		"from": from,
		"size": qp.PageSize,
	}

	boolQ["bool"].(map[string]interface{})["filter"] = filters

	// 获取索引前缀
	if indexPrefix == "" {
		indexPrefix = viper.GetString("elasticsearch.index_prefix")
	}
	if indexPrefix == "" {
		indexPrefix = "logs-"
	}
	indexPattern := indexPrefix + "*"

	return query, indexPattern, nil
}

// buildMessageBool 把 MessageCond 数组转换成 ES bool 查询
func buildMessageBool(message []Tag) map[string][]interface{} {
	// 结果：must / should / must_not
	result := map[string][]interface{}{
		"must":     {},
		"should":   {},
		"must_not": {},
	}

	for _, cond := range message {
		if cond.Tag == "" || cond.Value == "" {
			continue
		}

		// 根据 op 生成子句
		var clause map[string]interface{}
		switch strings.ToLower(cond.Op) {
		case "match":
			clause = map[string]interface{}{"match": map[string]interface{}{cond.Tag: cond.Value}}
		case "wildcard":
			clause = map[string]interface{}{"wildcard": map[string]interface{}{cond.Tag: cond.Value}}
		case "regexp":
			clause = map[string]interface{}{"regexp": map[string]interface{}{cond.Tag: cond.Value}}
		case "prefix":
			clause = map[string]interface{}{"prefix": map[string]interface{}{cond.Tag: cond.Value}}
		case "term":
			clause = map[string]interface{}{"term": map[string]interface{}{cond.Tag: cond.Value}}
		default:
			clause = map[string]interface{}{"match": map[string]interface{}{cond.Tag: cond.Value}}
		}

		// 根据 operator 放入对应数组
		switch strings.ToLower(cond.Operator) {
		case "must":
			result["must"] = append(result["must"], clause)
		case "should":
			result["should"] = append(result["should"], clause)
		case "must_not":
			result["must_not"] = append(result["must_not"], clause)
		default:
			result["must"] = append(result["must"], clause)
		}
	}
	return result
}

// parseTime 将字符串时间转为 time.Time
func parseTime(ts string) (time.Time, error) {
	if ts == "" {
		return time.Time{}, nil
	}
	return time.ParseInLocation("2006-01-02 15:04:05", ts, time.Local)
}
