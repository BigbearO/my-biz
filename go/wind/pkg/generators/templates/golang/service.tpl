package service

import (
	"context"
	"log/slog"

	paginationV1 "github.com/tx7do/go-crud/api/gen/go/pagination/v1"
	"google.golang.org/protobuf/types/known/emptypb"

	"{{.Module}}/pkg/bootstrap"
{{if .UseRepo}}
	"{{.Module}}/app/{{.Service}}/service/internal/data"
{{end}}
	{{.TargetApiPackage}} "{{.Module}}/api/gen/go/{{lower .TargetApiPackageName}}/service/{{lower .TargetApiPackageVersion}}"
{{- if not .IsSameApi}}
	{{.SourceApiPackage}} "{{.Module}}/api/gen/go/{{lower .SourceApiPackageName}}/service/{{lower .SourceApiPackageVersion}}"
{{end -}}
)

type {{.ClassName}} struct {
	{{.ServiceInterface}}

	log *slog.Logger

	{{.DataSourceVar}} {{.DataSourceType}}
}

func New{{.ClassName}}(
    bs *bootstrap.Bootstrap,
    {{.DataSourceVar}} {{.DataSourceType}},
) *{{.ClassName}} {
	return &{{.ClassName}}{
		log:  bs.Logger.With("module", "{{lower .Model}}/service"),
		{{.DataSourceVar}}:  {{.DataSourceVar}},
	}
}

func (s *{{.ClassName}}) List(ctx context.Context, req *paginationV1.PagingRequest) (*{{.SourceApiPackage}}.List{{pascal .Model}}Response, error) {
	return s.{{.DataSourceVar}}.List(ctx, req)
}

func (s *{{.ClassName}}) Get(ctx context.Context, req *{{.SourceApiPackage}}.Get{{pascal .Model}}Request) (*{{.SourceApiPackage}}.{{pascal .Model}}, error) {
	return s.{{.DataSourceVar}}.Get(ctx, req)
}

func (s *{{.ClassName}}) Create(ctx context.Context, req *{{.SourceApiPackage}}.Create{{pascal .Model}}Request) (*{{.SourceApiPackage}}.{{pascal .Model}}, error) {
	if req == nil || req.Data == nil {
		return nil, {{.SourceApiPackage}}.ErrorBadRequest("invalid parameter")
	}
{{if not .UseRepo}}
	if result, err := s.{{.DataSourceVar}}.Create(ctx, req); err != nil {
		return nil, err
	} else {
	    return result, nil
	}
{{else}}
    return s.{{.DataSourceVar}}.Create(ctx, req)
{{end -}}
}

func (s *{{.ClassName}}) Update(ctx context.Context, req *{{.SourceApiPackage}}.Update{{pascal .Model}}Request) (*emptypb.Empty, error) {
	if req == nil || req.Data == nil {
		return nil, {{.SourceApiPackage}}.ErrorBadRequest("invalid parameter")
	}
	if _, err := s.{{.DataSourceVar}}.Update(ctx, req); err != nil {
		return nil, err
	}
	return &emptypb.Empty{}, nil
}

func (s *{{.ClassName}}) Delete(ctx context.Context, req *{{.SourceApiPackage}}.Delete{{pascal .Model}}Request) (*emptypb.Empty, error) {
    if req == nil {
        return nil, {{.SourceApiPackage}}.ErrorBadRequest("invalid parameter")
    }
{{if and .UseRepo .IsGorm}}
	deleted, err := s.{{.DataSourceVar}}.Delete(ctx, req)
	if err != nil {
		return nil, err
	}
	if !deleted {
		return nil, {{.SourceApiPackage}}.ErrorNotFound("{{lower .Model}} not found")
	}
	return &emptypb.Empty{}, nil
{{else if .UseRepo}}
	if err := s.{{.DataSourceVar}}.Delete(ctx, req); err != nil {
		return nil, err
	}
	return &emptypb.Empty{}, nil
{{else}}
    return s.{{.DataSourceVar}}.Delete(ctx, req)
{{end -}}
}
