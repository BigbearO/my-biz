package api

import (
	"logViewer/application"
	"logViewer/infra/es"
	"net/http"

	"github.com/elastic/go-elasticsearch/v8"
	"github.com/gin-gonic/gin"
)

var EsCLinet *elasticsearch.Client = nil

func Search(c *gin.Context) {

	es.SearchLogsV2(c, EsCLinet)
}

func Insert(c *gin.Context) {
	application.InsertDemo(EsCLinet)
}

func Test(c *gin.Context) {
	c.JSON(http.StatusOK, es.Resp{Code: 200, Msg: "", Data: 1})

}
