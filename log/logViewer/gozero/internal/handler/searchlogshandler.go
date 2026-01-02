package handler

import (
	"context"
	"logViewer/gozero/internal/svc"
	"logViewer/gozero/internal/types"
	"logViewer/infra/es"
	"net/http"

	"github.com/zeromicro/go-zero/rest/httpx"
)

func NewSearchLogsHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req types.QueryParam
		if err := httpx.Parse(r, &req); err != nil {
			httpx.Error(w, err)
			return
		}

		// 转换 go-zero types.QueryParam 到 es.QueryParam
		esQP := convertToESQueryParam(req)

		// 调用共享的查询构建和执行逻辑
		query, indexPattern, err := es.BuildESQuery(esQP, svcCtx.Config.Elasticsearch.IndexPrefix)
		if err != nil {
			httpx.WriteJson(w, http.StatusInternalServerError, types.Resp{Code: 500, Msg: err.Error()})
			return
		}

		logs, total, err := es.ExecuteSearch(context.Background(), svcCtx.ESClient, query, indexPattern)
		if err != nil {
			httpx.WriteJson(w, http.StatusInternalServerError, types.Resp{Code: 500, Msg: err.Error()})
			return
		}

		// 转换 es.LogDoc 到 types.LogDoc
		typesLogs := make([]types.LogDoc, len(logs))
		for i, log := range logs {
			typesLogs[i] = types.LogDoc{
				Timestamp:  log.Timestamp,
				Level:      log.Level,
				Message:    log.Message,
				App:        log.App,
				TraceID:    log.TraceID,
				LoggerName: log.LoggerName,
				ThreadName: log.ThreadName,
				Env:        log.Env,
			}
		}

		httpx.WriteJson(w, http.StatusOK, types.LogResponse{
			Code: 0,
			Msg:  "success",
			Data: types.Data{
				Total: total,
				Logs:  typesLogs,
			},
		})
	}
}

// convertToESQueryParam 转换 go-zero QueryParam 到 ES QueryParam
func convertToESQueryParam(req types.QueryParam) es.QueryParam {
	messages := make([]es.Tag, len(req.Messages))
	for i, msg := range req.Messages {
		messages[i] = es.Tag{
			Op:    msg.Op,
			Value: msg.Value,
		}
	}

	return es.QueryParam{
		Level:    req.Level,
		App:      req.App,
		TraceID:  req.TraceID,
		Messages: messages,
		Env:      req.Env,
		Start:    req.Start,
		End:      req.End,
		Page:     req.Page,
		PageSize: req.PageSize,
	}
}
