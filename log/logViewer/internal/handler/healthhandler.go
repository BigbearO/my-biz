package handler

import (
	"logViewer/internal/types"
	"net/http"

	"github.com/zeromicro/go-zero/rest/httpx"
)

func NewHealthHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		httpx.WriteJson(w, http.StatusOK, types.LogResponse{
			Code: 200,
			Msg:  "ok",
			Data: types.Data{
				Total: 0,
				Logs:  []types.LogDoc{},
			},
		})
	}
}
