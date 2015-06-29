package com.phuna.amazonecs;

import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;

import java.util.List;
import java.util.logging.Logger;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.NetworkBinding;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.Task;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.google.common.base.Preconditions;

public class EcsDockerComputerLauncher extends DelegatingComputerLauncher {
	private static final Logger logger = Logger
			.getLogger(EcsDockerComputerLauncher.class.getName());

	// private EcsTaskTemplate template;

	protected EcsDockerComputerLauncher(EcsTaskTemplate template,
					    RunTaskResult runTaskResult,
					    int containerStartTimeout) {
	    super(makeLauncher(template, runTaskResult, containerStartTimeout));
	}

	private static ComputerLauncher makeLauncher(EcsTaskTemplate template,
						     RunTaskResult runTaskResult,
						     int containerStartTimeout) {
	    SSHLauncher sshLauncher = getSSHLauncher(runTaskResult, template, containerStartTimeout);
		return new RetryingComputerLauncher(sshLauncher);
	}

	private static SSHLauncher getSSHLauncher(RunTaskResult runTaskResult,
						  EcsTaskTemplate template,
						  int containerStartTimeout) {
		Preconditions.checkNotNull(template);

//		AmazonECSClient client = CommonUtils.getEcsClient();

		for (Failure failure : runTaskResult.getFailures()) {
			logger.info("Failed task: " + failure);
		}
		List<Task> tasks = runTaskResult.getTasks();
		logger.info("Tasks = " + tasks.size());
		if (tasks.size() == 0) {
			throw new RuntimeException("No task created");
		}
		String taskArn = tasks.get(0).getTaskArn();

		// Find host port for SSH connection
		int sshPort = Constants.SSH_PORT;
		int port = -1;
		String host = "";
		
		AwsCloud cloud = template.getParent();
		
		// Wait until container's status becomes RUNNING
		Container ctn = AWSUtils.waitForContainer(cloud, taskArn, containerStartTimeout);
		if (!ctn.getLastStatus().equals("RUNNING")) {
			throw new RuntimeException("Container takes too long time to start");
		}

		List<NetworkBinding> nbs = ctn.getNetworkBindings();
		logger.info("Network binding size = " + nbs.size());
		for (NetworkBinding nb : nbs) {
			logger.info("Container's binding: host = " + nb.getBindIP() + ", port = "
					+ nb.getHostPort());
			if (nb.getContainerPort() == sshPort) {
				port = nb.getHostPort();
				host = nb.getBindIP();
				
				break;
			}
		}

		if (host == "" || port == -1) {
			logger.warning("Failed to connect to the container");
			AWSUtils.stopTask(cloud, taskArn, template.getParent().isSameVPC(), containerStartTimeout);
			throw new RuntimeException("Cannot determine host/port to SSH into");
		}

		// host = "54.187.124.238";
//		host = "172.31.4.94";
		logger.info("container's private IP = " + AWSUtils.getTaskContainerPrivateAddress(cloud, taskArn));
		logger.info("container's public IP = " + AWSUtils.getTaskContainerPublicAddress(cloud, taskArn));
//		if (host.equals("0.0.0.0")) {
//			host = CommonUtils.getTaskContainerPublicAddress(taskArn);
//		}
		if (template.getParent().isSameVPC()) {
			logger.info("Use private address");
			host = AWSUtils.getTaskContainerPrivateAddress(cloud, taskArn);
		} else {
			logger.info("Use public address");
			host = AWSUtils.getTaskContainerPublicAddress(cloud, taskArn);
		}

		logger.info("Creating slave SSH launcher for " + host + ":" + port);

		if (!CommonUtils.waitForPort(host, port, containerStartTimeout)) {
		    throw new RuntimeException("Port took too long to become available");
		}

		StandardUsernameCredentials credentials = SSHLauncher
				.lookupSystemCredentials(template.getCredentialsId());

		// return new SSHLauncher();
		return new SSHLauncher(host, port, credentials, "", // jvmOptions
				"", // javaPath,
				"", // prefixStartSlaveCmd
				"", // suffixStartSlaveCmd
				template.getSSHLaunchTimeoutMinutes() * 60);

		// } catch (Exception e) {
		// throw new RuntimeException("Error: " + e);
		// }
	}

}
