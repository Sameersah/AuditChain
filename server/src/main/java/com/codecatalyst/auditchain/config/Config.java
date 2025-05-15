package com.codecatalyst.auditchain.config;

import java.util.List;

public class Config {
    public static final String NODE_ID = "169.254.27.203:50052";
  //  public static final String SELF_ADDRESS = "Sameer@0.0.0.0:50052";
    public static final List<String> PEER_ADDRESSES = List.of(
            "169.254.62.157:50051",
            "169.254.153.82:50051",
          "169.254.55.120:50051"

    );
}
