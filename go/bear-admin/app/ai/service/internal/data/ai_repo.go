package data

import (
	"context"
	"fmt"
	"strings"

	"github.com/cloudwego/eino-ext/components/model/openai"
	"github.com/cloudwego/eino/adk"
	"github.com/cloudwego/eino/schema"
	"github.com/go-kratos/kratos/v2/log"
	"github.com/spf13/viper"
	"github.com/tx7do/kratos-bootstrap/bootstrap"

	"bigbear.com/xxs-common/xxs-otel-log/logx"
)

type AiRepo struct {
	log    *log.Helper
	agent  adk.Agent
	runner *adk.Runner
}

func NewAiRepo(ctx *bootstrap.Context) *AiRepo {
	config := ctx.GetConfig()

	config.GetRegistry()
	background := context.Background()
	cm, err := openai.NewChatModel(background, &openai.ChatModelConfig{
		APIKey:  viper.GetString("ai.apikey"),
		Model:   viper.GetString("ai.model"),
		BaseURL: viper.GetString("ai.base_url"),
	})
	if err != nil {
		return nil
	}

	agent, err := adk.NewChatModelAgent(background, &adk.ChatModelAgentConfig{
		Name:        "assistant",
		Description: "A helpful assistant",
		Instruction: "You are a helpful assistant.",
		Model:       cm,
	})
	if err != nil {
		return nil
	}

	runner := adk.NewRunner(background, adk.RunnerConfig{
		Agent:           agent,
		EnableStreaming: true,
	})

	return &AiRepo{
		log:    logx.NewTracedHelper(ctx.GetLogger(), "ai/repo"),
		agent:  agent,
		runner: runner,
	}
}

func (s *AiRepo) Chat(input string) string {
	ctx := context.Background()
	history := []*schema.Message{
		schema.UserMessage(input),
	}

	events := s.runner.Run(ctx, history)
	return collectContentFromEvents2(events)
}

func collectContentFromEvents2(events *adk.AsyncIterator[*adk.AgentEvent]) string {
	var sb strings.Builder
	for {
		event, ok := events.Next()
		if !ok {
			break
		}
		println(1)
		if event.Err != nil {
			fmt.Printf("错误: %v", event.Err)
			break
		}

		if msg, err := event.Output.MessageOutput.GetMessage(); err == nil {
			fmt.Printf("Agent: %s\n", msg.Content)
			sb.WriteString(msg.Content)
		}
	}
	return sb.String()
}
