package main

import "my-cache/tcp"

func main() {
	tcp.ListenAndServe0(":8089")

}
