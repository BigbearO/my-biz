package bootstrap

import (
	"fmt"
	"os"

	"github.com/go-kratos/kratos/v3/encoding"
	goprotojson "google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
)

// jsonProtoCodec 以 protojson 语义实现名为 "json" 的编解码器。
//
// kratos v3 把 REST 的 application/json 默认切到了标准库 json(按 Go 字段名/json tag 匹配),
// 而 protobuf 生成结构体(尤其 proto3 optional)的 json tag 是 snake_case——前端发 camelCase
// 会静默丢字段。注册本 codec 恢复 v2 行为:请求/响应均走 protojson(接受 json_name 与
// 原始字段名,输出 camelCase)。
type jsonProtoCodec struct{}

func (jsonProtoCodec) Marshal(v any) ([]byte, error) {
	os.Stderr.WriteString("[codec] Marshal called\n")
	m, ok := v.(proto.Message)
	if !ok {
		return nil, fmt.Errorf("json codec: %T 不是 proto.Message", v)
	}
	return goprotojson.MarshalOptions{EmitUnpopulated: true}.Marshal(m)
}

func (jsonProtoCodec) Unmarshal(data []byte, v any) error {
	os.Stderr.WriteString("[codec] Unmarshal called\n")
	m, ok := v.(proto.Message)
	if !ok {
		return fmt.Errorf("json codec: %T 不是 proto.Message", v)
	}
	return goprotojson.UnmarshalOptions{DiscardUnknown: true}.Unmarshal(data, m)
}

func (jsonProtoCodec) Name() string { return "json" }

var _ encoding.Codec = (*jsonProtoCodec)(nil)

func init() {
	encoding.RegisterCodec(&jsonProtoCodec{})
}
