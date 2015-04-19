package com.phuna.amazonecs;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ecs.AmazonECSClient;

public interface AwsCloud {
	AmazonECSClient getEcsClient();
	AmazonEC2Client getEc2Client();
}
