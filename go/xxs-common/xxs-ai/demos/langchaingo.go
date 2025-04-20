package main

import (
	"context"
	"fmt"
	"github.com/tmc/langchaingo/llms"
	"github.com/tmc/langchaingo/llms/openai"
	"log"
)

func TestLangChainGo(token string, model string) {
	llm, err := openai.New(openai.WithModel(model), openai.WithToken(token))
	if err != nil {
		log.Fatal(err)
	}
	ctx := context.Background()

	content := []llms.MessageContent{
		llms.TextParts(llms.ChatMessageTypeSystem, "You are a go programming expert"),
		llms.TextParts(llms.ChatMessageTypeHuman, "Why is go a good language for production LLM applications?"),
	}

	completion, err := llm.GenerateContent(ctx, content, llms.WithStreamingFunc(func(ctx context.Context, chunk []byte) error {
		fmt.Print(string(chunk))
		return nil
	}))
	if err != nil {
		log.Fatal(err)
	}
	_ = completion
}

func TestLangChainGoWithToken(token string) {
	llm, err := openai.New(openai.WithModel("gpt-4o"), openai.WithToken(token))
	if err != nil {
		log.Fatal(err)
	}
	ctx := context.Background()

	content := []llms.MessageContent{
		llms.TextParts(llms.ChatMessageTypeSystem, "You are a go programming expert"),
		llms.TextParts(llms.ChatMessageTypeHuman, "Why is go a good language for production LLM applications?"),
	}

	completion, err := llm.GenerateContent(ctx, content, llms.WithStreamingFunc(func(ctx context.Context, chunk []byte) error {
		fmt.Print(string(chunk))
		return nil
	}))
	if err != nil {
		log.Fatal(err)
	}
	_ = completion
}
