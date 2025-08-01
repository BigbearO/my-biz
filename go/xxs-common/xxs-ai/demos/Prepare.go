package main

import (
	"github.com/spf13/viper"
	"log"
	"xxs-ai/common/config"
)

func init() {
	viper.SetConfigName("config")      // 配置文件名称(无扩展名)
	viper.SetConfigType("yaml")        // 或viper.SetConfigType("YAML")
	viper.AddConfigPath("./resources") // 配置文件路径,从项目开始
	err := viper.ReadInConfig()        // 查找并读取配置文件
	if err != nil {                    // 处理读取配置文件的错误
		log.Printf("reslove config file err %v\n", err)
	} else {
		config.SdkConf = &config.SDKConfig{
			EmptyMessagesLimit: viper.GetInt32("my.sdk.ai.emptyMessagesLimit"),
			ApiVersion:         viper.GetString("my.sdk.ai.apiVersion"),
		}
	}

}
