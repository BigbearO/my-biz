package api

import (
	"context"
	"logViewer/infra/es"
	"net/http"

	elasticsearch "github.com/elastic/go-elasticsearch/v8"
	"github.com/gin-gonic/gin"
)

var esClient *elasticsearch.Client

// SetESClient 设置 ES 客户端（依赖注入）
func SetESClient(client *elasticsearch.Client) {
	esClient = client
}

// SearchLogs 日志查询 Handler（使用共享的查询逻辑）
func SearchLogs(c *gin.Context) {
	var qp es.QueryParam
	if err := c.ShouldBindQuery(&qp); err != nil {
		c.JSON(http.StatusBadRequest, es.Resp{
			Code: 400,
			Msg:  err.Error(),
		})
		return
	}

	// 调用共享的查询构建和执行逻辑
	query, indexPattern, err := es.BuildESQuery(qp, "")
	if err != nil {
		c.JSON(http.StatusInternalServerError, es.Resp{
			Code: 500,
			Msg:  err.Error(),
		})
		return
	}

	logs, total, err := es.ExecuteSearch(context.Background(), esClient, query, indexPattern)
	if err != nil {
		c.JSON(http.StatusInternalServerError, es.Resp{
			Code: 500,
			Msg:  err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, es.Resp{
		Code: 0,
		Msg:  "success",
		Data: gin.H{
			"total": total,
			"logs":  logs,
		},
	})
}

// InsertDemo 插入演示数据
func InsertDemo(c *gin.Context) {
	c.JSON(http.StatusOK, es.Resp{
		Code: 200,
		Msg:  "Demo endpoint - not implemented",
	})
}

// TestHealth 健康检查
func TestHealth(c *gin.Context) {
	c.JSON(http.StatusOK, es.Resp{
		Code: 200,
		Msg:  "OK",
		Data: gin.H{"status": "healthy"},
	})
}
