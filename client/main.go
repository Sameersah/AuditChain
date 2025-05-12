package main

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"
	"time"
	"encoding/pem"

	common "github.com/sameersah/auditchain/proto_gen/common"
	pb "github.com/sameersah/auditchain/proto_gen/file_audit"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

type AuditClient struct {
	conn   *grpc.ClientConn
	client pb.FileAuditServiceClient
	key    *rsa.PrivateKey
}

func NewAuditClient(serverAddr string) (*AuditClient, error) {
	privateKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return nil, fmt.Errorf("failed to generate RSA key: %v", err)
	}

	conn, err := grpc.Dial(serverAddr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		return nil, fmt.Errorf("failed to connect to server: %v", err)
	}

	client := pb.NewFileAuditServiceClient(conn)
	return &AuditClient{
		conn:   conn,
		client: client,
		key:    privateKey,
	}, nil
}

func (c *AuditClient) Close() {
	if c.conn != nil {
		c.conn.Close()
	}
}

func (c *AuditClient) getPublicKeyPEM() string {
	publicKeyBytes, err := x509.MarshalPKIXPublicKey(&c.key.PublicKey)
	if err != nil {
		log.Fatalf("Failed to marshal public key: %v", err)
	}

	pemBlock := &pem.Block{
		Type:  "PUBLIC KEY",
		Bytes: publicKeyBytes,
	}

	return string(pem.EncodeToMemory(pemBlock))
}

func (c *AuditClient) signAudit(audit *common.FileAudit) (string, error) {
	// Mimic the Python dict used for signing
	auditMap := map[string]interface{}{
		"req_id": audit.ReqId,
		"file_info": map[string]interface{}{
			"file_id":   audit.FileInfo.FileId,
			"file_name": audit.FileInfo.FileName,
		},
		"user_info": map[string]interface{}{
			"user_id":   audit.UserInfo.UserId,
			"user_name": audit.UserInfo.UserName,
		},
		"access_type": audit.AccessType,
		"timestamp":   audit.Timestamp,
	}

	// Marshal to JSON with sorted keys
	jsonBytes, err := json.Marshal(auditMap)
	if err != nil {
		return "", fmt.Errorf("failed to marshal audit map: %v", err)
	}

    println("JSON bytes:", string(jsonBytes))

	// Hash the JSON string
	hash := sha256.Sum256(jsonBytes)

	// Sign using RSA PKCS#1 v1.5 with SHA-256
	signature, err := rsa.SignPKCS1v15(rand.Reader, c.key, crypto.SHA256, hash[:])
	if err != nil {
		return "", fmt.Errorf("failed to sign audit: %v", err)
	}

	// Base64 encode the signature
	return base64.StdEncoding.EncodeToString(signature), nil
}

func (c *AuditClient) SubmitAudit(fileID, fileName, userID, userName string, accessType common.AccessType) (*pb.FileAuditResponse, error) {
	// Construct audit record
	audit := &common.FileAudit{
		ReqId: fmt.Sprintf("req_%d", time.Now().UnixNano()),
		FileInfo: &common.FileInfo{
			FileId:   fileID,
			FileName: fileName,
		},
		UserInfo: &common.UserInfo{
			UserId:   userID,
			UserName: userName,
		},
		AccessType: accessType,
		Timestamp:  time.Now().Unix(),
		PublicKey:  c.getPublicKeyPEM(),
	}

	// Sign the audit
	signature, err := c.signAudit(audit)
	if err != nil {
		return nil, err
	}
	audit.Signature = signature

	// Send to server
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	response, err := c.client.SubmitAudit(ctx, audit)
	if err != nil {
		return nil, fmt.Errorf("failed to submit audit: %v", err)
	}

	return response, nil
}

func main() {
	serverAddr := flag.String("server", "localhost:50051", "The server address")
	fileID := flag.String("file-id", "", "File ID")
	fileName := flag.String("file-name", "", "File name")
	userID := flag.String("user-id", "", "User ID")
	userName := flag.String("user-name", "", "User name")
	accessTypeStr := flag.String("access-type", "READ", "Access type (READ, WRITE, UPDATE, DELETE)")
	flag.Parse()

	if *fileID == "" || *fileName == "" || *userID == "" || *userName == "" {
		fmt.Println("Error: All flags are required")
		flag.Usage()
		os.Exit(1)
	}

	var accessType common.AccessType
	switch *accessTypeStr {
	case "READ":
		accessType = common.AccessType_READ
	case "WRITE":
		accessType = common.AccessType_WRITE
	case "UPDATE":
		accessType = common.AccessType_UPDATE
	case "DELETE":
		accessType = common.AccessType_DELETE
	default:
		fmt.Printf("Error: Invalid access type: %s\n", *accessTypeStr)
		os.Exit(1)
	}

	client, err := NewAuditClient(*serverAddr)
	if err != nil {
		log.Fatalf("Failed to create client: %v", err)
	}
	defer client.Close()

	response, err := client.SubmitAudit(*fileID, *fileName, *userID, *userName, accessType)
	if err != nil {
		log.Fatalf("Failed to submit audit: %v", err)
	}

	fmt.Printf("Audit submitted successfully!\n")
	fmt.Printf("Request ID: %s\n", response.ReqId)
	fmt.Printf("Blockchain TX Hash: %s\n", response.BlockchainTxHash)
	fmt.Printf("Status: %s\n", response.Status)
	if response.Status == "failure" {
		fmt.Printf("Error: %s\n", response.ErrorMessage)
	}

	if response.BlockHeader != nil {
		fmt.Printf("\nBlock Information:\n")
		fmt.Printf("Block Hash: %s\n", response.BlockHeader.BlockHash)
		fmt.Printf("Block Number: %d\n", response.BlockHeader.BlockNumber)
		fmt.Printf("Timestamp: %d\n", response.BlockHeader.Timestamp)
		fmt.Printf("Previous Block Hash: %s\n", response.BlockHeader.PreviousBlockHash)
		fmt.Printf("Merkle Root: %s\n", response.BlockHeader.MerkleRoot)
	}
}
