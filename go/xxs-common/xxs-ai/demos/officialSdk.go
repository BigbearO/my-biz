package main

import (
	"context"
	"fmt"
	"github.com/openai/openai-go"
	"github.com/openai/openai-go/option"
)

//openAI官方Sdk

func test1() {
	client := openai.NewClient(
		option.WithAPIKey("My API Key"), // defaults to os.LookupEnv("OPENAI_API_KEY")
	)
	chatCompletion, err := client.Chat.Completions.New(context.TODO(), openai.ChatCompletionNewParams{
		Messages: openai.F([]openai.ChatCompletionMessageParamUnion{
			openai.UserMessage("Say this is a test"),
		}),
		Model: openai.F(openai.ChatModelGPT4o),
	})
	if err != nil {
		panic(err.Error())
	}
	str := fmt.Sprintf("res%s", chatCompletion.SystemFingerprint)
	fmt.Printf(str)
}
