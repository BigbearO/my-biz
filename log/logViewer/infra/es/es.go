package es

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"strings"
	"time"

	elasticsearch "github.com/elastic/go-elasticsearch/v8"
	_ "github.com/elastic/go-elasticsearch/v8/esapi"
	"github.com/gin-gonic/gin"
	"github.com/spf13/viper"
)

// 日志索引名（按天生成的 index，如 log-2024-06-01）
//const logIndexPattern = "logs-*"

// 日志文档结构（可根据实际字段扩展）
type LogDoc struct {
	Timestamp  time.Time `json:"@timestamp"`
	Level      string    `json:"level"` // debug/info/warn/error
	Message    string    `json:"message"`
	App        string    `json:"app"`
	TraceID    string    `json:"trace_id,omitempty"`
	LoggerName string    `json:"logger_name"`
	ThreadName string    `json:"thread_name"`
	Env        string    `json:"env"`
}

// 初始化 ES 客户端
func NewESClient() *elasticsearch.Client {
	addressList := os.Getenv("LOG_ES_ADDR")
	var addresses []string = nil
	if addressList != "" {
		addresses = strings.Split(addressList, ",")
	}
	if len(addresses) == 0 {
		addresses = viper.GetStringSlice("elasticsearch.addresses")
	}

	username := viper.GetString("elasticsearch.username")
	password := viper.GetString("elasticsearch.password")

	if addresses == nil {
		panic("elasticsearch.addresses is nil")
	}

	cfg := elasticsearch.Config{
		Addresses: addresses, // 按需修改
		Username:  username,  // 若有认证
		Password:  password,
	}
	es, err := elasticsearch.NewClient(cfg)
	if err != nil {
		log.Fatalf("create es client err: %v", err)
	}
	return es
}

// 统一响应格式
type Resp struct {
	Code int         `json:"code"`
	Msg  string      `json:"msg"`
	Data interface{} `json:"data,omitempty"`
}

// 将字符串时间转为 time.Time
func parseTime(ts string) (time.Time, error) {
	if ts == "" {
		return time.Time{}, nil
	}
	// 先按 RFC3339 解析
	//if t, err := time.Parse(time.RFC3339, ts); err == nil {
	//	return t, nil
	//}
	//// 再按 Unix 秒
	//if sec, err := strconv.ParseInt(ts, 10, 64); err == nil {
	//	return time.Unix(sec, 0), nil
	//}
	//return time.Time{}, fmt.Errorf("invalid time format")

	return time.ParseInLocation("2006-01-02 15:04:05", ts, time.Local)
}

//
//// 查询日志接口
//func SearchLogs(es *elasticsearch.Client) gin.HandlerFunc {
//	return func(c *gin.Context) {
//		var qp QueryParam
//		if err := c.ShouldBindQuery(&qp); err != nil {
//			c.JSON(http.StatusBadRequest, Resp{Code: 400, Msg: err.Error()})
//			return
//		}
//
//		// 构造 bool 查询
//		boolQ := map[string]interface{}{
//			"bool": map[string]interface{}{
//				"filter": []interface{}{},
//			},
//		}
//		filters := boolQ["bool"].(map[string]interface{})["filter"].([]interface{})
//
//		if qp.Level != "" {
//			filters = append(filters, map[string]interface{}{
//				"term": map[string]interface{}{"level": qp.Level},
//			})
//		}
//		if qp.Service != "" {
//			filters = append(filters, map[string]interface{}{
//				"term": map[string]interface{}{"service": qp.Service},
//			})
//		}
//
//		if qp.TraceID != "" {
//			filters = append(filters, map[string]interface{}{
//				"term": map[string]interface{}{"trace_id": qp.TraceID},
//			})
//		}
//
//		// 时间范围
//		start, _ := parseTime(qp.Start)
//		end, _ := parseTime(qp.End)
//		if !start.IsZero() || !end.IsZero() {
//			rangeQ := map[string]interface{}{}
//			if !start.IsZero() {
//				rangeQ["gte"] = start.Format(time.RFC3339)
//			}
//			if !end.IsZero() {
//				rangeQ["lte"] = end.Format(time.RFC3339)
//			}
//			filters = append(filters, map[string]interface{}{
//				"range": map[string]interface{}{
//					"@timestamp": rangeQ,
//				},
//			})
//		}
//
//		// 分页
//		if qp.Page <= 0 {
//			qp.Page = 1
//		}
//		if qp.PageSize <= 0 || qp.PageSize > 100 {
//			qp.PageSize = 20
//		}
//		from := (qp.Page - 1) * qp.PageSize
//
//		query := map[string]interface{}{
//			"query": boolQ,
//			"sort": []interface{}{
//				map[string]interface{}{"@timestamp": map[string]string{"order": "desc"}},
//			},
//			"from": from,
//			"size": qp.PageSize,
//		}
//
//		var buf bytes.Buffer
//		if err := json.NewEncoder(&buf).Encode(query); err != nil {
//			c.JSON(http.StatusInternalServerError, Resp{Code: 500, Msg: err.Error()})
//			return
//		}
//
//		resp, err := es.Search(
//			es.Search.WithContext(context.Background()),
//			es.Search.WithIndex(logIndexPattern),
//			es.Search.WithBody(&buf),
//			es.Search.WithTrackTotalHits(true),
//		)
//		if err != nil {
//			c.JSON(http.StatusInternalServerError, Resp{Code: 500, Msg: err.Error()})
//			return
//		}
//		defer resp.Body.Close()
//
//		if resp.IsError() {
//			c.JSON(http.StatusInternalServerError, Resp{Code: 500, Msg: resp.String()})
//			return
//		}
//
//		var esResp struct {
//			Hits struct {
//				Total struct {
//					Value int `json:"value"`
//				} `json:"total"`
//				Hits []struct {
//					Source LogDoc `json:"_source"`
//				} `json:"hits"`
//			} `json:"hits"`
//		}
//		if err := json.NewDecoder(resp.Body).Decode(&esResp); err != nil {
//			c.JSON(http.StatusInternalServerError, Resp{Code: 500, Msg: err.Error()})
//			return
//		}
//
//		logs := make([]LogDoc, 0, len(esResp.Hits.Hits))
//		for _, h := range esResp.Hits.Hits {
//			logs = append(logs, h.Source)
//		}
//
//		c.JSON(http.StatusOK, Resp{
//			Code: 0,
//			Msg:  "success",
//			Data: gin.H{
//				"total": esResp.Hits.Total.Value,
//				"logs":  logs,
//			},
//		})
//	}
//}

func SearchLogsV2(c *gin.Context, es *elasticsearch.Client) {
	var qp QueryParam
	if err := c.ShouldBindQuery(&qp); err != nil {
		c.JSON(http.StatusBadRequest, Resp{Code: 400, Msg: err.Error()})
		return
	}

	// 构造 bool 查询
	boolQ := map[string]interface{}{
		"bool": map[string]interface{}{
			"filter": []interface{}{},
		},
	}
	filters := boolQ["bool"].(map[string]interface{})["filter"].([]interface{})

	if qp.Messages != nil && len(qp.Messages) > 0 {
		//filters = append(filters, map[string]interface{}{
		//	"match": map[string]interface{}{
		//		"message": qp.Message, // ES 会对 message 分词后做模糊匹配
		//	},
		//})

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
	start, _ := parseTime(qp.Start)
	end, _ := parseTime(qp.End)
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

	queryJSON, _ := json.MarshalIndent(query, "", "  ")
	log.Printf("Elasticsearch Query:\n%s\n", queryJSON)

	var buf bytes.Buffer
	if err := json.NewEncoder(&buf).Encode(query); err != nil {

		c.JSON(http.StatusInternalServerError, Resp{Code: 500, Msg: err.Error()})
		return
	}

	logIndexPattern := viper.GetString("elasticsearch.index_prefix") + "*"

	resp, err := es.Search(
		es.Search.WithContext(context.Background()),
		es.Search.WithIndex(logIndexPattern),
		es.Search.WithBody(&buf),
		es.Search.WithTrackTotalHits(true),
	)
	if err != nil {
		fmt.Println(err)
		c.JSON(http.StatusInternalServerError, Resp{Code: 500, Msg: err.Error()})
		return
	}
	defer resp.Body.Close()

	if resp.IsError() {
		fmt.Println(resp)
		c.JSON(http.StatusInternalServerError, Resp{Code: 500, Msg: resp.String()})
		return
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
		c.JSON(http.StatusInternalServerError, Resp{Code: 500, Msg: err.Error()})
		return
	}

	logs := make([]LogDoc, 0, len(esResp.Hits.Hits))
	for _, h := range esResp.Hits.Hits {
		logs = append(logs, h.Source)
	}

	c.JSON(http.StatusOK, Resp{
		Code: 0,
		Msg:  "success",
		Data: gin.H{
			"total": esResp.Hits.Total.Value,
			"logs":  logs,
		},
	})
}

// 把 MessageCond 数组转换成 ES bool 查询
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
		switch cond.Op {
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
		switch cond.Operator {
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
