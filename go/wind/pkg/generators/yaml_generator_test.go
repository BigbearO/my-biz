package generators

import (
	"context"
	"testing"

	"github.com/tx7do/go-utils/code_generator"
)

func TestYamlGenerator_Template_ServerYaml(t *testing.T) {
	g := NewYamlGenerator()

	opts := code_generator.Options{
		OutDir: t.TempDir(),
	}

	if _, err := g.GenerateServerYaml(context.Background(), opts); err != nil {
		t.Fatalf("Generate server.yaml failed: %v", err)
	}
}

func TestYamlGenerator_Template_DataYaml(t *testing.T) {
	g := NewYamlGenerator()

	opts := code_generator.Options{
		OutDir: t.TempDir(),
	}

	if _, err := g.GenerateDataYaml(context.Background(), opts); err != nil {
		t.Fatalf("Generate data.yaml failed: %v", err)
	}
}

func TestYamlGenerator_Template_BootstrapYaml(t *testing.T) {
	g := NewYamlGenerator()

	opts := code_generator.Options{
		OutDir: t.TempDir(),
		Vars: map[string]any{
			"Project": "demo",
			"Service": "admin",
		},
	}

	if _, err := g.GenerateBootstrapYaml(context.Background(), opts); err != nil {
		t.Fatalf("Generate bootstrap.yaml failed: %v", err)
	}
}
