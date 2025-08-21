package main

import (
	"context"
	"errors"
	"fmt"
	"github.com/cloudwego/eino-ext/components/model/openai"
	"github.com/cloudwego/eino/components/prompt"
	"github.com/cloudwego/eino/schema"
	"io"
)

var (
	ModelType                    = "deepseek-chat"
	OwnerAPIKey                  = "YOUR_API_KEY"
	DeepSeekBaseURL              = "https://api.deepseek.com"
	SystemMessageDefaultTemplate = `你是一个{role}。你需要用{style}的语气回答问题。你的目标是帮助程序员保持积极乐观的心态，提供技术建议的同时也要关注他们的心理健康。`
	UserMessageDefaultTemplate   = `问题: {question}`
)

func main() {
	ctx := context.Background()
	template := createPrompt()
	chatModelConfig := &openai.ChatModelConfig{
		Model:   ModelType,
		APIKey:  OwnerAPIKey,
		BaseURL: DeepSeekBaseURL,
	}
	chatModel, err := createChatModel(ctx, chatModelConfig)
	if err != nil {
		fmt.Println("创建聊天模型时发生错误：", err)
		return
	}
	var chatHistory []*schema.Message
	example := []*schema.Message{
		schema.UserMessage("我觉得自己写的代码太烂了"),
		schema.AssistantMessage("每个程序员都经历过这个阶段！重要的是你在不断学习和进步。让我们一起看看代码，我相信通过重构和优化，它会变得更好。记住，Rome wasn't built in a day，代码质量是通过持续改进来提升的。", nil),
	}

	for {
		fmt.Print("请输入你的问题：")
		// 读取用户输入
		var userQuestion string
		count, err := fmt.Scanln(&userQuestion)
		if err != nil {
			fmt.Println("读取输入时发生错误：", err)
			return
		}
		if count <= 0 {
			fmt.Println("没有输入任何内容")
			return
		}
		message, err := getMessage(ctx, template, chatHistory, example, userQuestion)
		if err != nil {
			fmt.Println("生成消息时发生错误：", err)
			return
		}

		// 实现流式输出
		stream, err := chatModel.Stream(ctx, message)
		if err != nil {
			fmt.Println("生成消息时发生错误：", err)
			return
		}
		defer stream.Close()

		for {
			var context string
			response, err := stream.Recv()
			if errors.Is(err, io.EOF) {
				// 将用户的问题和模型的回答添加到聊天历史中
				resp := schema.AssistantMessage(context, nil)
				chatHistory = append(chatHistory, resp)
				fmt.Println()
				break
			}
			if err != nil {
				fmt.Println("接收流式响应时发生错误：", err)
				return
			}
			fmt.Print(response.Content)
			context += response.Content
		}
	}
}

func createPrompt() *prompt.DefaultChatTemplate {
	// 创建模板，使用 FString 格式
	return prompt.FromMessages(schema.FString,
		// 系统消息模板
		schema.SystemMessage(SystemMessageDefaultTemplate),

		// 插入可选的示例对话
		schema.MessagesPlaceholder("examples", true),

		// 插入必需的对话历史
		schema.MessagesPlaceholder("chat_history", false),

		// 用户消息模板
		schema.UserMessage(UserMessageDefaultTemplate),
	)
}

func getMessage(ctx context.Context, template *prompt.DefaultChatTemplate, chatHistory, example []*schema.Message, userQuestion string) (result []*schema.Message, err error) {
	// 使用模板生成消息
	messages, err := template.Format(ctx, map[string]any{
		"role":     "程序员鼓励师",
		"style":    "积极、温暖且专业",
		"question": userQuestion,
		// 对话历史（必需的）
		"chat_history": chatHistory,
		// 示例对话（可选的）
		"examples": example,
	})
	if err != nil {
		return nil, err
	}

	return messages, err
}

func createChatModel(ctx context.Context, config *openai.ChatModelConfig) (*openai.ChatModel, error) {
	chatModel, err := openai.NewChatModel(ctx, config)
	if err != nil {
		return nil, err
	}

	return chatModel, nil
}
