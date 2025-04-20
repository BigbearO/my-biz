package main

import (
	"bufio"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strings"
	"testing"
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

	parentDir := filepath.Dir(dir)
	fmt.Println("fumulu:", parentDir)
}
func Test1(t *testing.T) {
	fmt.Println("客户端准备开启。。。。")
	//conn, err := net.Dial("tcp", "127.0.0.1:8088")
	conn, err := net.Dial("tcp", "192.168.50.16:8088")
	if err != nil {
		fmt.Println("qidong fail")
		fmt.Println(err)
		return
	}

	fmt.Println("start ,,,")

	go proc(conn)
	var exitFlag = <-exitChan
	if exitFlag {
		fmt.Println("stoping")
		return
	}
}

var exitChan = make(chan bool, 1)

func proc(conn net.Conn) {
	defer conn.Close()
	reader := bufio.NewReader(os.Stdin)  //标准输入流
	line, err := reader.ReadString('\n') //每次读一-行
	if err != nil {
		fmt.Println(err)
	}
	line = strings.Trim(line, "\r\n")
	if line == "bye" {
		exitChan <- true
		return
	}

	length, err1 := conn.Write([]byte(line))
	if err1 != nil {
		fmt.Println(err1)
	}
	fmt.Println("客户端连接成功", conn.RemoteAddr().String(), "并且写了", length, "个字节")

}
