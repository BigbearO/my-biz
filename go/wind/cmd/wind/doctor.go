package main

import (
	"github.com/wind-scaffold/wind/internal/doctor"
)

func init() {
	rootCmd.AddCommand(doctor.CmdDoctor)
}
