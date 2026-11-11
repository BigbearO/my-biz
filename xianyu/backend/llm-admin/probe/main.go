package main

import (
	"encoding/json"
	"fmt"

	"google.golang.org/protobuf/encoding/protojson"

	v1 "github.com/xianyu/llm-admin/api/gen/go/llm_provider/service/v1"
)

func main() {
	body := []byte(`{"data":{"name":"probe-name","baseUrl":"https://probe.example","apiKey":"sk-probe","status":1}}`)

	req := &v1.CreateLlmProviderRequest{}
	if err := protojson.Unmarshal(body, req); err != nil {
		fmt.Println("PROTOJSON_ERR:", err)
	} else {
		fmt.Printf("PROTOJSON → name=%q baseUrl=%q apiKey=%q\n", req.GetData().GetName(), req.GetData().GetBaseUrl(), req.GetData().GetApiKey())
	}

	req2 := &v1.CreateLlmProviderRequest{}
	if err := json.Unmarshal(body, req2); err != nil {
		fmt.Println("STDJSON_ERR:", err)
	} else {
		fmt.Printf("STDJSON   → name=%q baseUrl=%q apiKey=%q\n", req2.GetData().GetName(), req2.GetData().GetBaseUrl(), req2.GetData().GetApiKey())
	}
}
