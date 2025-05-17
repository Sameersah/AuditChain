# AuditChain

AuditChain is a blockchain-based system designed to manage and verify audits securely and efficiently. It includes features like block proposal, voting, committing, heartbeat monitoring, and leader election.

## Table of Contents
- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Technologies Used](#technologies-used)
- [Setup and Installation](#setup-and-installation)
- [Usage](#usage)
- [Protobuf Definitions](#protobuf-definitions)
- [Key Components](#key-components)
- [Contributing](#contributing)
- [License](#license)

---

## Overview
AuditChain is a distributed system where nodes collaborate to maintain a blockchain ledger of audits. It ensures data integrity, fault tolerance, and leader-based consensus for block proposals and commits.

---

## Features
- **Block Proposal and Voting**: Nodes propose blocks containing audits, and other nodes vote on their validity.
- **Heartbeat Monitoring**: Nodes send periodic heartbeats to indicate their availability.
- **Leader Election**: A leader is elected when the current leader is unavailable.
- **Audit Management**: Audits are added to a mempool and removed upon block commitment.
- **gRPC Communication**: Nodes communicate using gRPC for high-performance RPC calls.

---

## Architecture
The system follows a leader-based consensus model:
1. **Leader Node**: Proposes blocks and coordinates block commits.
2. **Follower Nodes**: Validate block proposals and vote on their acceptance.
3. **Heartbeat Mechanism**: Ensures leader availability and triggers elections if the leader is unresponsive.

---

## Technologies Used
- **Languages**: Go, Java
- **Frameworks**: gRPC, Protocol Buffers
- **Build Tools**: Gradle (Java), `go.mod` (Go)
- **Storage**: In-memory and file-based block storage
- **Networking**: gRPC for inter-node communication

---

## Setup and Installation

### Prerequisites
- **Java**: JDK 11 or higher
- **Go**: Version 1.18 or higher
- **Protocol Buffers**: `protoc` compiler installed
- **Gradle**: Version 7.0 or higher

### Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/sameersah/auditchain.git
   cd auditchain


Compile Protocol Buffers:  
protoc --go_out=. --go-grpc_out=. proto/blockchain.proto
protoc --java_out=./server/src/main/java proto/blockchain.proto
Build the Java server:  
cd server
./gradlew build
Run the Go client:  
cd client
go run main.go
<hr></hr>
Usage
Running the Server
Start the Java server:

java -jar build/libs/auditchain-server.jar

Running the Client
Run the Go client to interact with the server:

go run main.go

Configuration
Update the Config.java file to set the node ID and peer addresses:

public static final String NODE_ID = "your-node-id";
public static final List<String> PEER_ADDRESSES = List.of("peer1-address", "peer2-address");


<hr></hr>
Protobuf Definitions
The system uses Protocol Buffers (proto/blockchain.proto) for defining messages and services. Key services include:  
ProposeBlock: Propose a new block.
CommitBlock: Commit a block to the blockchain.
SendHeartbeat: Send periodic heartbeats.
GetBlock: Retrieve a block by ID.
<hr></hr>
Key Components
1. Leader Election
Detects leader failure using heartbeats.
Triggers elections and updates the leader.
2. Heartbeat Monitoring
Periodically sends heartbeats to peers.
Updates the last heartbeat timestamp for each node.
3. Block Proposal and Commit
Proposes blocks with audits from the mempool.
Validates and commits blocks to persistent storage.
4. Audit Management
Adds audits to the mempool.
Removes audits upon block commitment.
<hr></hr>
Contributing
Contributions are welcome! Please follow these steps:  
Fork the repository.
Create a feature branch: git checkout -b feature-name.
Commit your changes: git commit -m "Add feature-name".
Push to the branch: git push origin feature-name.
Open a pull request.
<hr></hr>
License
This project is licensed under the MIT License. See the LICENSE file for details.


This `README.md` provides a comprehensive overview of the project, its features, setup instructions, and key components. Adjust the content as needed to fit your specific project details.

