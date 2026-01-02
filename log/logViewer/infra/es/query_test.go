package es

import (
	"encoding/json"
	"fmt"
	"testing"
)

func TestBuildESQuery(t *testing.T) {
	// 测试用例1: 空参数查询
	var qp1 QueryParam
	query1, _, err := BuildESQuery(qp1, "")
	if err != nil {
		t.Errorf("Empty query failed: %v", err)
	}
	fmt.Println("Test Case 1 - Empty Query:")
	fmt.Println(query1)
	fmt.Println()

	// 测试用例2: 单个条件查询
	qp2 := QueryParam{
		Messages: []Tag{
			{
				Op:    "and",
				Value: "error",
			},
		},
	}
	query2, _, err := BuildESQuery(qp2, "")
	if err != nil {
		t.Errorf("Single condition query failed: %v", err)
	}
	fmt.Println("Test Case 2 - Single Condition Query:")
	fmt.Println(query2)
	fmt.Println()

	// 测试用例3: 流水线查询示例 ((A OR B) AND (NOT C)) AND D
	qp3 := QueryParam{
		Messages: []Tag{
			{
				Op:    "and",
				Value: "A",
			},
			{
				Op:    "or",
				Value: "B",
			},
			{
				Op:    "not",
				Value: "C",
			},
			{
				Op:    "and",
				Value: "D",
			},
		},
	}
	query3, _, err := BuildESQuery(qp3, "")
	if err != nil {
		t.Errorf("Pipeline query failed: %v", err)
	}
	fmt.Println("Test Case 3 - Pipeline Query ((A OR B) AND (NOT C)) AND D:")
	json3, err := json.Marshal(query3)
	fmt.Println(string(json3))
	fmt.Println()

	// 测试用例4: 不同逻辑运算符查询
	qp4 := QueryParam{
		Messages: []Tag{
			{
				Op:    "and",
				Value: "error",
			},
			{
				Op:    "or",
				Value: "warning",
			},
			{
				Op:    "and",
				Value: "critical",
			},
		},
	}
	query4, _, err := BuildESQuery(qp4, "")
	if err != nil {
		t.Errorf("Different logical operators query failed: %v", err)
	}
	fmt.Println("Test Case 4 - Different Logical Operators Query:")
	json4, err := json.Marshal(query4)
	fmt.Println(string(json4))
	fmt.Println()

	// 测试用例5: 带其他过滤条件的查询
	qp5 := QueryParam{
		Messages: []Tag{
			{
				Op:    "and",
				Value: "error",
			},
			{
				Op:    "not",
				Value: "warning",
			},
		},
		Level:    "error",
		App:      "my-app",
		TraceID:  "1234567890",
		Env:      "production",
		Start:    "2023-01-01 00:00:00",
		End:      "2023-01-02 00:00:00",
		Page:     2,
		PageSize: 20,
	}
	query5, _, err := BuildESQuery(qp5, "")
	if err != nil {
		t.Errorf("Query with other filters failed: %v", err)
	}
	fmt.Println("Test Case 5 - Query with Other Filters:")
	json5, err := json.Marshal(query5)
	fmt.Println(string(json5))
	fmt.Println()

	// 测试用例6: 只有过滤条件，没有Messages
	qp6 := QueryParam{
		Level:    "warn",
		App:      "my-app",
		TraceID:  "1234567890",
		Env:      "development",
		Start:    "2023-01-01 00:00:00",
		End:      "2023-01-02 00:00:00",
		Page:     1,
		PageSize: 10,
	}
	query6, _, err := BuildESQuery(qp6, "")
	if err != nil {
		t.Errorf("Query with only filters failed: %v", err)
	}
	fmt.Println("Test Case 6 - Query with Only Filters:")
	json6, err := json.Marshal(query6)
	fmt.Println(string(json6))
	fmt.Println()

	// 测试用例7: 多个OR条件查询
	qp7 := QueryParam{
		Messages: []Tag{
			{
				Op:    "or",
				Value: "apple",
			},
			{
				Op:    "or",
				Value: "banana",
			},
			{
				Op:    "or",
				Value: "orange",
			},
		},
	}
	query7, _, err := BuildESQuery(qp7, "")
	if err != nil {
		t.Errorf("Multiple OR conditions query failed: %v", err)
	}
	fmt.Println("Test Case 7 - Multiple OR Conditions Query:")
	json7, err := json.Marshal(query7)
	fmt.Println(string(json7))
	fmt.Println()

	// 测试用例8: 复杂组合查询
	qp8 := QueryParam{
		Messages: []Tag{
			{
				Op:    "and",
				Value: "product",
			},
			{
				Op:    "or",
				Value: "add",
			},
			{
				Op:    "or",
				Value: "update",
			},
			{
				Op:    "not",
				Value: "delete",
			},
		},
	}
	query8, _, err := BuildESQuery(qp8, "")
	if err != nil {
		t.Errorf("Complex combination query failed: %v", err)
	}
	fmt.Println("Test Case 8 - Complex Combination Query:")
	json8, err := json.Marshal(query8)
	fmt.Println(string(json8))
	fmt.Println()
}
