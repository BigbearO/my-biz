package api

import (
	"context"
	"logViewer/infra/es"

	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/protocol/consts"
	"github.com/elastic/go-elasticsearch/v8"
)

var esClient *elasticsearch.Client

// SetESClient 设置 ES 客户端（依赖注入）
func SetESClient(client *elasticsearch.Client) {
	esClient = client
}

// SearchLogs 日志查询 Handler（使用共享的查询逻辑）
func SearchLogs(ctx context.Context, c *app.RequestContext) {
	var qp es.QueryParam
	if err := c.Bind(&qp); err != nil {
		c.JSON(consts.StatusBadRequest, es.Resp{
			Code: 400,
			Msg:  err.Error(),
		})
		return
	}

	// 调用共享的查询构建和执行逻辑
	query, indexPattern, err := es.BuildESQuery(qp, "")
	if err != nil {
		c.JSON(consts.StatusInternalServerError, es.Resp{
			Code: 500,
			Msg:  err.Error(),
		})
		return
	}

	logs, total, err := es.ExecuteSearch(context.Background(), esClient, query, indexPattern)
	if err != nil {
		c.JSON(consts.StatusInternalServerError, es.Resp{
			Code: 500,
			Msg:  err.Error(),
		})
		return
	}

	c.JSON(consts.StatusOK, es.Resp{
		Code: 0,
		Msg:  "success",
		Data: map[string]interface{}{
			"total": total,
			"logs":  logs,
		},
	})
}

// InsertDemo 插入演示数据
func InsertDemo(ctx context.Context, c *app.RequestContext) {
	c.JSON(consts.StatusOK, es.Resp{
		Code: 200,
		Msg:  "Demo endpoint - not implemented",
	})
}

// TestHealth 健康检查
func TestHealth(ctx context.Context, c *app.RequestContext) {
	c.JSON(consts.StatusOK, es.Resp{
		Code: 200,
		Msg:  "OK",
		Data: map[string]interface{}{"status": "healthy"},
	})
}
