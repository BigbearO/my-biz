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
		boolRoot := boolQ["bool"].(map[string]interface{})

		// 处理must条件
		if mustClauses, ok := msgBool["must"].([]interface{}); ok && len(mustClauses) > 0 {
			boolRoot["must"] = mustClauses
		}

		// 处理should条件
		if shouldClauses, ok := msgBool["should"].([]interface{}); ok && len(shouldClauses) > 0 {
			boolRoot["should"] = shouldClauses
			// 检查是否已经设置了minimum_should_match
			if minShouldMatch, ok := msgBool["minimum_should_match"]; ok {
				boolRoot["minimum_should_match"] = minShouldMatch
			} else {
				// 默认 should 至少匹配 1 条
				boolRoot["minimum_should_match"] = 1
			}
		}

		// 处理must_not条件
		if mustNotClauses, ok := msgBool["must_not"].([]interface{}); ok && len(mustNotClauses) > 0 {
			boolRoot["must_not"] = mustNotClauses
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

// buildMessageBool 把 MessageCond 数组转换成 ES bool 查询，实现从左到右的过滤器流水线
func buildMessageBool(message []Tag) map[string]interface{} {
	// 结果：must / should / must_not
	result := map[string]interface{}{
		"must":     []interface{}{},
		"should":   []interface{}{},
		"must_not": []interface{}{},
	}

	if len(message) == 0 {
		return result
	}

	// 处理第一个条件
	firstCond := message[0]
	if firstCond.Value == "" {
		return result
	}

	// 根据 op 生成第一个条件的子句
	firstClause := buildClause(firstCond)

	// 处理第一个条件的逻辑运算符
	switch strings.ToLower(firstCond.Op) {
	case "and":
		result["must"] = append(result["must"].([]interface{}), firstClause)
	case "or":
		result["should"] = append(result["should"].([]interface{}), firstClause)
		result["minimum_should_match"] = 1
	case "not":
		result["must_not"] = append(result["must_not"].([]interface{}), firstClause)
	default:
		// 默认使用AND逻辑
		result["must"] = append(result["must"].([]interface{}), firstClause)
	}

	// 如果只有一个条件，直接返回
	if len(message) == 1 {
		return result
	}

	// 从第二个条件开始，构建流水线
	for i := 1; i < len(message); i++ {
		currentCond := message[i]
		if currentCond.Value == "" {
			continue
		}

		// 构建当前条件的子句
		currentClause := buildClause(currentCond)

		// 创建新的bool查询，将上一步的结果作为一个子句
		prevResult := map[string]interface{}{}
		// 复制当前result的内容到prevResult
		for k, v := range result {
			prevResult[k] = v
		}

		// 重置result，准备构建新的bool查询
		result = map[string]interface{}{
			"must":     []interface{}{},
			"should":   []interface{}{},
			"must_not": []interface{}{},
		}

		// 将上一步的结果包装成一个bool查询子句
		prevBoolClause := map[string]interface{}{
			"bool": prevResult,
		}

		// 根据当前条件的逻辑运算符，与上一步结果组合
		switch strings.ToLower(currentCond.Op) {
		case "and":
			// AND逻辑：将上一步结果和当前条件都放入must数组
			result["must"] = append(result["must"].([]interface{}), prevBoolClause)
			result["must"] = append(result["must"].([]interface{}), currentClause)
		case "or":
			// OR逻辑：将上一步结果和当前条件都放入should数组
			result["should"] = append(result["should"].([]interface{}), prevBoolClause)
			result["should"] = append(result["should"].([]interface{}), currentClause)
			result["minimum_should_match"] = 1
		case "not":
			// NOT逻辑：在must数组中放入上一步结果，以及一个包含must_not的bool查询
			result["must"] = append(result["must"].([]interface{}), prevBoolClause)
			notBoolClause := map[string]interface{}{
				"bool": map[string]interface{}{
					"must_not": []interface{}{currentClause},
				},
			}
			result["must"] = append(result["must"].([]interface{}), notBoolClause)
		default:
			// 默认使用AND逻辑
			result["must"] = append(result["must"].([]interface{}), prevBoolClause)
			result["must"] = append(result["must"].([]interface{}), currentClause)
		}
	}

	return result
}

// buildClause 根据Tag生成查询子句，只使用value字段进行ES文字匹配，实现全匹配
func buildClause(cond Tag) map[string]interface{} {
	// 默认使用message字段进行精确短语匹配
	return map[string]interface{}{"match_phrase": map[string]interface{}{"message": cond.Value}}
}

// parseTime 将字符串时间转为 time.Time
func parseTime(ts string) (time.Time, error) {
	if ts == "" {
		return time.Time{}, nil
	}
	return time.ParseInLocation("2006-01-02 15:04:05", ts, time.Local)
}
