package main

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"flag"
	"fmt"
	"log"
	"os"
	"time"

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
	// Generate RSA key pair
	privateKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return nil, fmt.Errorf("failed to generate RSA key: %v", err)
	}

	// Connect to server
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

	// Remove PEM encoding and use raw base64
	return base64.StdEncoding.EncodeToString(publicKeyBytes)
}

func (c *AuditClient) signAudit(audit *common.FileAudit) (string, error) {
	// Create a hash of the audit data
	data := audit.ReqId +
		audit.FileInfo.FileId +
		audit.FileInfo.FileName +
		audit.UserInfo.UserId +
		audit.UserInfo.UserName +
		audit.AccessType.String() +
		fmt.Sprintf("%d", audit.Timestamp)

	// Create SHA256 hash
	hash := sha256.Sum256([]byte(data))

	// Sign the hash
	signature, err := rsa.SignPKCS1v15(rand.Reader, c.key, crypto.SHA256, hash[:])
	if err != nil {
		return "", fmt.Errorf("failed to sign audit: %v", err)
	}

	// Use standard base64 encoding
	return base64.StdEncoding.EncodeToString(signature), nil
}

func (c *AuditClient) SubmitAudit(fileID, fileName, userID, userName string, accessType common.AccessType) (*pb.FileAuditResponse, error) {
	// Create audit record
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

	// Submit to server
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	response, err := c.client.SubmitAudit(ctx, audit)
	if err != nil {
		return nil, fmt.Errorf("failed to submit audit: %v", err)
	}

	return response, nil
}

func main() {
	serverAddr := flag.String("server", "localhost:50051", "The server address in the format of host:port")
	fileID := flag.String("file-id", "", "File ID to audit")
	fileName := flag.String("file-name", "", "File name to audit")
	userID := flag.String("user-id", "", "User ID performing the action")
	userName := flag.String("user-name", "", "User name performing the action")
	accessTypeStr := flag.String("access-type", "READ", "Access type (READ, WRITE, UPDATE, DELETE)")
	flag.Parse()

	// Validate required flags
	if *fileID == "" || *fileName == "" || *userID == "" || *userName == "" {
		fmt.Println("Error: All flags are required")
		flag.Usage()
		os.Exit(1)
	}

	// Parse access type
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

	// Create client
	client, err := NewAuditClient(*serverAddr)
	if err != nil {
		log.Fatalf("Failed to create client: %v", err)
	}
	defer client.Close()

	// Submit audit
	response, err := client.SubmitAudit(*fileID, *fileName, *userID, *userName, accessType)
	if err != nil {
		log.Fatalf("Failed to submit audit: %v", err)
	}

	// Print response
	fmt.Printf("Audit submitted successfully!\n")
	fmt.Printf("Request ID: %s\n", response.ReqId)
	fmt.Printf("Blockchain TX Hash: %s\n", response.BlockchainTxHash)
	fmt.Printf("Status: %s\n", response.Status)
	if response.Status == "failure" {
		fmt.Printf("Error: %s\n", response.ErrorMessage)
	}

	// Print block header information if available
	if response.BlockHeader != nil {
		fmt.Printf("\nBlock Information:\n")
		fmt.Printf("Block Hash: %s\n", response.BlockHeader.BlockHash)
		fmt.Printf("Block Number: %d\n", response.BlockHeader.BlockNumber)
		fmt.Printf("Timestamp: %d\n", response.BlockHeader.Timestamp)
		fmt.Printf("Previous Block Hash: %s\n", response.BlockHeader.PreviousBlockHash)
		fmt.Printf("Merkle Root: %s\n", response.BlockHeader.MerkleRoot)
	}
}
