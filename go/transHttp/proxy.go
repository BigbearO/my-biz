package main

import (
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
)

type ProxyHandler struct {
	routes map[string]string
}

func NewProxyHandler(routes []RouteConfig) *ProxyHandler {
	routeMap := make(map[string]string)
	for _, route := range routes {
		routeMap[route.PathPrefix] = route.TargetURL
	}
	return &ProxyHandler{routes: routeMap}
}

func (p *ProxyHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	// 查找匹配的路径前缀
	var targetURL string
	var matchedPrefix string
	fmt.Printf("req come in,%s\n", r.URL.Path)

	for prefix, target := range p.routes {
		if strings.HasPrefix(r.URL.Path, prefix) {
			targetURL = target
			matchedPrefix = prefix
			break
		}
	}

	if targetURL == "" {
		http.Error(w, "No matching route found", http.StatusNotFound)
		return
	}

	// 解析目标URL
	target, err := url.Parse(targetURL)
	if err != nil {
		http.Error(w, fmt.Sprintf("Invalid target URL: %v", err), http.StatusInternalServerError)
		return
	}

	// 创建新的请求
	newURL := *r.URL
	// 1. 修剪掉匹配的前缀
	trimmedPath := strings.TrimPrefix(r.URL.Path, matchedPrefix)
	if trimmedPath == "" {
		trimmedPath = "/"
	}
	// 2. 构建新路径：target路径 + 修剪后的路径
	// 确保target.Path末尾有斜杠，trimmedPath开头没有斜杠
	targetPath := target.Path
	if !strings.HasSuffix(targetPath, "/") {
		targetPath += "/"
	}
	if strings.HasPrefix(trimmedPath, "/") {
		trimmedPath = strings.TrimPrefix(trimmedPath, "/")
	}
	newURL.Path = targetPath + trimmedPath

	newURL.Scheme = target.Scheme
	newURL.Host = target.Host

	proxyReq, err := http.NewRequest(r.Method, newURL.String(), r.Body)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to create proxy request: %v", err), http.StatusInternalServerError)
		return
	}

	// 复制请求头
	for key, values := range r.Header {
		for _, value := range values {
			proxyReq.Header.Add(key, value)
		}
	}

	// 设置必要的头信息
	proxyReq.Header.Set("X-Forwarded-For", r.RemoteAddr)
	proxyReq.Header.Set("X-Forwarded-Host", r.Host)
	proxyReq.Header.Set("X-Forwarded-Proto", "http")

	// 创建HTTP客户端并执行请求
	client := &http.Client{}
	resp, err := client.Do(proxyReq)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to proxy request: %v", err), http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	// 复制响应头
	for key, values := range resp.Header {
		for _, value := range values {
			w.Header().Add(key, value)
		}
	}

	// 设置状态码
	w.WriteHeader(resp.StatusCode)

	// 复制响应体
	if _, err := io.Copy(w, resp.Body); err != nil {
		fmt.Printf("Failed to copy response body: %v\n", err)
	}
}
