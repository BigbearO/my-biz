package main

import (
	"fmt"
	"os"
)

func main() {

	TestName()
}

func TestName() {
	dir, err := os.Getwd()
	if err != nil {
		fmt.Println(err)
	} else {
		fmt.Println("当前程序所在目录:", dir)
	}
}
