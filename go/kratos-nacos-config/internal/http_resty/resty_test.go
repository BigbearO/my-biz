package http_resty

import (
	"fmt"
	"testing"
)

// Notice 详细的demo参考官方文档的例子：https://github.com/go-resty/resty
type plusMeta struct {
	Uid uint32 `json:"uid"`
}

func TestGetRequest(t *testing.T) {

	client := NewRestyHttpClient()

	meta := plusMeta{Uid: 13227292}

	resp, err := client.R().
		SetBody(meta).
		EnableTrace().
		Post("http://user-api.go-testing.5eplaycdn.com/user/plus/get-info")

	// Explore response object
	fmt.Println("Response Info:")
	fmt.Println("  Error      :", err)
	fmt.Println("  Status Code:", resp.StatusCode())
	fmt.Println("  Status     :", resp.Status())
	fmt.Println("  Proto      :", resp.Proto())
	fmt.Println("  Time       :", resp.Time())
	fmt.Println("  Received At:", resp.ReceivedAt())
	fmt.Println("  Body       :", resp)
	fmt.Println()

	// Explore trace info
	fmt.Println("Request Trace Info:")
	ti := resp.Request.TraceInfo()
	fmt.Println("  DNSLookup     :", ti.DNSLookup)
	fmt.Println("  ConnTime      :", ti.ConnTime)
	fmt.Println("  TCPConnTime   :", ti.TCPConnTime)
	fmt.Println("  TLSHandshake  :", ti.TLSHandshake)
	fmt.Println("  ServerTime    :", ti.ServerTime)
	fmt.Println("  ResponseTime  :", ti.ResponseTime)
	fmt.Println("  TotalTime     :", ti.TotalTime)
	fmt.Println("  IsConnReused  :", ti.IsConnReused)
	fmt.Println("  IsConnWasIdle :", ti.IsConnWasIdle)
	fmt.Println("  ConnIdleTime  :", ti.ConnIdleTime)
	fmt.Println("  RequestAttempt:", ti.RequestAttempt)
	fmt.Println("  RemoteAddr    :", ti.RemoteAddr.String())

	/*
		Response Info:
		  Error      : <nil>
		  Status Code: 200
		  Status     : 200 OK
		  Proto      : HTTP/1.1
		  Time       : 5.019581959s
		  Received At: 2023-06-12 16:16:07.815538 +0800 CST m=+5.021425710
		  Body       : hello GET whw

		Request Trace Info:
		  DNSLookup     : 0s
		  ConnTime      : 526.208µs
		  TCPConnTime   : 313.709µs
		  TLSHandshake  : 0s
		  ServerTime    : 5.018870584s
		  ResponseTime  : 386.25µs
		  TotalTime     : 5.019581959s
		  IsConnReused  : false
		  IsConnWasIdle : false
		  ConnIdleTime  : 0s
		  RequestAttempt: 1
		  RemoteAddr    : 127.0.0.1:9099

	*/

}
