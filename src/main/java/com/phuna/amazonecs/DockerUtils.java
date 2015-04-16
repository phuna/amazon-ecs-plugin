package com.phuna.amazonecs;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;

public class DockerUtils {
	public static final int DOCKER_PORT = 2375;

	public static DockerClient getDockerClient(String host, int port) {
		DockerClientConfig.DockerClientConfigBuilder config = DockerClientConfig
				.createDefaultConfigBuilder().withUri(
						"http://" + host + ":" + port);
		return DockerClientBuilder.getInstance(config.build())
				.withDockerCmdExecFactory(new DockerCmdExecFactoryImpl())
				.build();
	}
	
	public static boolean waitForContainerExit(DockerClient dockerClient, String containerId) {
		for (int i = 0; i < Constants.RETRIES; i++) {
			InspectContainerResponse r = dockerClient.inspectContainerCmd(containerId).exec();
			if (!r.getState().isRunning()) {
				return true;
			}
			
			try {
				Thread.sleep(Constants.WAIT_TIME_MS);
			} catch (InterruptedException e) {
				// no-op
			}
		}
		return false;
	}
}
