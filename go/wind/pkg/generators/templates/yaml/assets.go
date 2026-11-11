package yaml

import _ "embed"

var TemplateMap = map[string][]byte{
	"bootstrap_yaml.tpl": BootstrapYamlTemplate,
	"server_yaml.tpl":    ServerYamlTemplate,
	"data_yaml.tpl":      DataYamlTemplate,
}

//go:embed bootstrap_yaml.tpl
var BootstrapYamlTemplate []byte

//go:embed server_yaml.tpl
var ServerYamlTemplate []byte

//go:embed data_yaml.tpl
var DataYamlTemplate []byte
