package service

import (
	"context"
	"fmt"
	"log"
	"strings"

	"github.com/cloudwego/eino-ext/components/model/openai"
	"github.com/cloudwego/eino/adk"
	"github.com/cloudwego/eino/schema"
	"github.com/spf13/viper"
)

type AIService struct {
	agent  adk.Agent
	runner *adk.Runner
}

func NewAIService(ctx context.Context) (*AIService, error) {
	cm, err := openai.NewChatModel(ctx, &openai.ChatModelConfig{
		APIKey:  viper.GetString("ai.apikey"),
		Model:   viper.GetString("ai.model"),
		BaseURL: viper.GetString("ai.base_url"),
	})
	if err != nil {
		return nil, err
	}

	agent, err := adk.NewChatModelAgent(ctx, &adk.ChatModelAgentConfig{
		Name:        "assistant",
		Description: "A helpful assistant",
		Instruction: "You are a helpful assistant.",
		Model:       cm,
	})
	if err != nil {
		return nil, err
	}

	runner := adk.NewRunner(ctx, adk.RunnerConfig{
		Agent:           agent,
		EnableStreaming: false,
	})

	return &AIService{
		agent:  agent,
		runner: runner,
	}, nil
}

func (s *AIService) Chat(input string) string {
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
			log.Printf("错误: %v", event.Err)
			break
		}

		if msg, err := event.Output.MessageOutput.GetMessage(); err == nil {
			fmt.Printf("Agent: %s\n", msg.Content)
			sb.WriteString(msg.Content)
		}
	}
	return sb.String()
}
