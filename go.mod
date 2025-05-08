module github.com/sameersah/auditchain/client

go 1.21

require (
	github.com/sameersah/auditchain v0.0.0
	google.golang.org/grpc v1.58.3
	google.golang.org/protobuf v1.33.0
)

replace github.com/sameersah/auditchain => ../

require (
	github.com/golang/protobuf v1.5.3 // indirect
	golang.org/x/net v0.20.0 // indirect
	golang.org/x/sys v0.16.0 // indirect
	golang.org/x/text v0.14.0 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20240123012728-ef4313101c80 // indirect
)
