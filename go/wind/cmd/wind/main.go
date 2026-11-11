package main

import (
	"log"

	"github.com/spf13/cobra"

	"github.com/wind-scaffold/wind/internal/generate"
	"github.com/wind-scaffold/wind/internal/project"
)

var rootCmd = &cobra.Command{
	Use:   "wind",
	Short: "wind CLI",
	Long:  "wind 是基于 kratos v3 官方组件的自研脚手架 CLI。",
}

func init() {
	rootCmd.AddCommand(project.CmdProject)
	rootCmd.AddCommand(generate.CmdGenerate)
	rootCmd.AddCommand(versionCmd)
}

func main() {
	if err := rootCmd.Execute(); err != nil {
		log.Fatal(err)
	}
}
