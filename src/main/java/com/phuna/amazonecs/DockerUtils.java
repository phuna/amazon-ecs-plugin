package com.phuna.amazonecs;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

public class DockerUtils {
	public static final int DOCKER_PORT = 2375;
	
	public static DockerClient getDockerClient(String host, int port) {
		DockerClientConfig.DockerClientConfigBuilder config = DockerClientConfig
	            .createDefaultConfigBuilder()
	            .withUri("http://" + host + ":" + port);
	    return DockerClientBuilder.getInstance(config.build()).build();
	}
}
