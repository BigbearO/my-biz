package boot

import "kratosNacosConfig/internal/conf"

func NewBootConf() *BootConf {
	return &BootConf{}
}

func NewBootLog(conf *conf.Bootstrap) *BootLog {
	return &BootLog{
		conf: conf,
	}
}
