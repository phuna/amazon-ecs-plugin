package com.phuna.amazonecs;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.google.common.base.Strings;

public class Utils {
	public static AmazonECSClient getEcsClient(String accessKeyId, String secretAccessKey) {
		AmazonECSClient client = null;
		if (!Strings.isNullOrEmpty(accessKeyId) &&
				!Strings.isNullOrEmpty(secretAccessKey)) {
			BasicAWSCredentials awsCredentials = new BasicAWSCredentials(accessKeyId, secretAccessKey);
			client = new AmazonECSClient(awsCredentials); 
		} else {
			client = new AmazonECSClient();
		}
		
        String endpoint = System.getenv("AWS_ECS_ENDPOINT");
        if (endpoint == null || "".equals(endpoint)) {
            endpoint = "https://ecs.us-west-2.amazonaws.com";
        }
        client.setEndpoint(endpoint);
        String cluster = System.getenv("AWS_ECS_CLUSTER");
        if (cluster == null || "".equals(cluster)) {
            cluster = "default";
        }
        return client;
	}
}
