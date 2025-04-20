package config

type SDKConfig struct {
	EmptyMessagesLimit int32  `mapstructure:"emptyMessagesLimit" default:"30"`
	ApiVersion         string `mapstructure:"apiVersion" default:"v1"`
}

var SdkConf *SDKConfig
