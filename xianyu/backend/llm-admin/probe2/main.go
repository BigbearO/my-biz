package main

import (
	"fmt"

	"github.com/go-kratos/kratos/v3/encoding"

	_ "github.com/xianyu/llm-admin/pkg/bootstrap"
)

func main() {
	c := encoding.GetCodec("json")
	fmt.Printf("codec for json = %T\n", c)
}
