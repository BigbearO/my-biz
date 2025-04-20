package main

import (
	"context"
	"fmt"
	"github.com/sashabaranov/go-openai"
	client2 "xxs-ai/common/client"
	"xxs-ai/common/config"
)

func main() {

	//demo1()
	fmt.Println("Hello cs lk ctdpy")

	fmt.Printf("sss=%d", config.SdkConf.EmptyMessagesLimit)

}

func demo1() {
	client := client2.NewClientWithCustom("", "")

	//openai.ChatCompletionMessage{}
	resp, err := client.CreateChatCompletion(
		context.Background(),
		openai.ChatCompletionRequest{
			Model: openai.GPT3Dot5Turbo,
			Messages: []openai.ChatCompletionMessage{
				{
					Role:    openai.ChatMessageRoleUser,
					Content: "Hello!",
				},
			},
		},
	)

	if err != nil {
		fmt.Printf("ChatCompletion error: %v\n", err)
		return
	}

	fmt.Println(resp.Choices[0].Message.Content)
}
