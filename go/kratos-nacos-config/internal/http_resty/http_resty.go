package http_resty

import "github.com/google/wire"

var ProviderSet = wire.NewSet(NewRestyHttpClient)
