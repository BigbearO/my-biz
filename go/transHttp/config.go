package main

import (
	"encoding/json"
	"fmt"
	"os"
)

type RouteConfig struct {
	PathPrefix string `json:"path_prefix"`
	TargetURL  string `json:"target_url"`
}

type Config struct {
	Port   int           `json:"port"`
	Routes []RouteConfig `json:"routes"`
}

func LoadConfig(filename string) (*Config, error) {
	file, err := os.Open(filename)
	if err != nil {
		return nil, fmt.Errorf("failed to open config file: %v", err)
	}
	defer file.Close()

	var config Config
	decoder := json.NewDecoder(file)
	if err := decoder.Decode(&config); err != nil {
		return nil, fmt.Errorf("failed to decode config: %v", err)
	}

	return &config, nil
}
