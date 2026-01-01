package main

import (
	"encoding/json"
	"fmt"
	"logViewer/internal/handler"
	"logViewer/internal/types"
	"testing"
)

func TestBuildMessageBool(t *testing.T) {
	fmt.Println("测试修改后的 buildMessageBool 方法")
	fmt.Println("===============================")

	// 测试用例1: 一个tag {"111","and"} - 不管op是什么都是message需要满足包含111
	fmt.Println("\n测试用例1: 单个标签 - 包含111")
	tags1 := []types.Tag{
		{Value: "111", Op: "and"},
	}
	result1 := handler.BuildMessageBool(tags1)
	json1, _ := json.MarshalIndent(result1, "", "  ")
	fmt.Println(string(json1))

	// 测试用例2: 两个tag {"111","and"},{"222",""} - message需要满足包含111且包含222
	fmt.Println("\n测试用例2: 包含111且包含222")
	tags2 := []types.Tag{
		{Value: "111", Op: "and"},
		{Value: "222", Op: ""},
	}
	result2 := handler.BuildMessageBool(tags2)
	json2, _ := json.MarshalIndent(result2, "", "  ")
	fmt.Println(string(json2))

	// 测试用例3: {"111","or"},{"222",""} - message需要满足包含111或者包含222
	fmt.Println("\n测试用例3: 包含111或者包含222")
	tags3 := []types.Tag{
		{Value: "111", Op: "or"},
		{Value: "222", Op: ""},
	}
	result3 := handler.BuildMessageBool(tags3)
	json3, _ := json.MarshalIndent(result3, "", "  ")
	fmt.Println(string(json3))

	// 测试用例4: {"111","not"},{"222",""} - message需要满足包含111且不包含222
	fmt.Println("\n测试用例4: 包含111且不包含222")
	tags4 := []types.Tag{
		{Value: "111", Op: "not"},
		{Value: "222", Op: ""},
	}
	result4 := handler.BuildMessageBool(tags4)
	json4, _ := json.MarshalIndent(result4, "", "  ")
	fmt.Println(string(json4))

	// 测试用例5: 复杂组合 {"111","and"},{"222","or"},{"333","not"},{"444",""}
	fmt.Println("\n测试用例5: 复杂组合 - 包含111且(包含222或者不包含333)且不包含444")
	tags5 := []types.Tag{
		{Value: "111", Op: "and"},
		{Value: "222", Op: "or"},
		{Value: "333", Op: "not"},
		{Value: "444", Op: ""},
	}
	result5 := handler.BuildMessageBool(tags5)
	json5, _ := json.MarshalIndent(result5, "", "  ")
	fmt.Println(string(json5))
}
