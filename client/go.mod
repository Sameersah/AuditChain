module github.com/sameersah/auditchain/client

go 1.23.0

toolchain go1.24.3

require (
	github.com/sameersah/auditchain v0.0.0
	google.golang.org/grpc v1.72.0
)

require (
	golang.org/x/net v0.40.0 // indirect
	golang.org/x/sys v0.33.0 // indirect
	golang.org/x/text v0.25.0 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20250428153025-10db94c68c34 // indirect
	google.golang.org/protobuf v1.36.6 // indirect
)

replace github.com/sameersah/auditchain => ../
