package main

import (
	article "bytedangcedemo/article/kitex_gen/article/articleservice"
	"log"
)

func main() {
	svr := article.NewServer(new(ArticleServiceImpl))

	err := svr.Run()

	if err != nil {
		log.Println(err.Error())
	}
}
