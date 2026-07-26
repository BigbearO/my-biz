package service

import (
	"context"
	"net/http"

	"github.com/go-kratos/kratos/v2/log"
	"github.com/tx7do/kratos-bootstrap/bootstrap"

	"bear-admin/app/ai/service/internal/data"

	"bigbear.com/xxs-common/common-auth-go"
	"bigbear.com/xxs-common/xxs-otel-log/logx"

	aiconfpb "bear-admin/api/gen/go/ai/conf/v1"
	aipb "bear-admin/api/gen/go/ai/service/v1"
)

type AiService struct {
	aipb.UnimplementedChatServiceServer
	aipb.UnimplementedHelloServiceServer
	log    *log.Helper
	aiRepo *data.AiRepo
}

func NewAiService(ctx *bootstrap.Context, repo *data.AiRepo) *AiService {
	svc := &AiService{
		aiRepo: repo,
		log:    logx.NewTracedHelper(ctx.GetLogger(), "ai/service"),
	}

	var initCtx = context.Background()
	if cfg, ok := ctx.GetCustomConfig("AiWrapper"); ok {
		wrapper := cfg.(*aiconfpb.AiWrapper)
		if wrapper.Ai != nil {
			svc.log.WithContext(initCtx).Info("AI Config loaded - apiKey: ", wrapper.Ai.GetApiKey(), ", model: ", wrapper.Ai.GetModel(), ", baseUrl: ", wrapper.Ai.GetBaseUrl())
		}
	} else {
		svc.log.WithContext(initCtx).Warn("AI Config not found")
	}

	return svc
}

func (s *AiService) Chat(ctx context.Context, req *aipb.ChatRequest) (*aipb.ChatResponse, error) {
	s.log.WithContext(ctx).Info("Chat receive message: ", req.GetMessage())

	return &aipb.ChatResponse{
		Content: "Echo: " + req.GetMessage(),
	}, nil
}

func (s *AiService) SayHello(ctx context.Context, req *aipb.SayHelloRequest) (*aipb.SayHelloResponse, error) {
	u, ok := auth.CurrentUser(ctx)
	userId := ""
	if ok {
		userId = u.UserID
	}
	s.log.WithContext(ctx).Info("SayHello receive name: ", req.GetName(), " | userId: ", userId)
	return &aipb.SayHelloResponse{
		Message: "hello " + req.GetName(),
	}, nil
}

// func HandleChatStream(ctx context.Context, w http.ResponseWriter, r *http.Request) {
func HandleChatStream(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("X-Accel-Buffering", "no")

	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "Streaming not supported", http.StatusInternalServerError)
		return
	}
	// todo
	message := r.URL.Query().Get("message")
	if message == "" {
		message = "Hello"
	}

	for i := 0; i < 5; i++ {
		resp := &aipb.ChatStreamResponse{
			Content: "Stream chunk " + message,
			Done:    false,
		}

		_, err := w.Write([]byte("data: " + resp.GetContent() + "\n\n"))
		if err != nil {
			return
		}
		flusher.Flush()
	}

	w.Write([]byte("data: [DONE]\n\n"))
	flusher.Flush()
}
