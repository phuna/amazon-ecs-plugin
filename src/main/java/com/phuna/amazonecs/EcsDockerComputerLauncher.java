package com.phuna.amazonecs;

import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.RuntimeErrorException;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.NetworkBinding;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.StartTaskResult;
import com.amazonaws.services.ecs.model.StopTaskRequest;
import com.amazonaws.services.ecs.model.StopTaskResult;
import com.amazonaws.services.ecs.model.Task;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.google.common.base.Preconditions;

public class EcsDockerComputerLauncher extends DelegatingComputerLauncher {
	private static final Logger logger = Logger
			.getLogger(EcsDockerComputerLauncher.class.getName());

	// private EcsTaskTemplate template;

	protected EcsDockerComputerLauncher(EcsTaskTemplate template,
			RunTaskResult runTaskResult) {
		super(makeLauncher(template, runTaskResult));
	}

	private static ComputerLauncher makeLauncher(EcsTaskTemplate template,
			RunTaskResult runTaskResult) {
		SSHLauncher sshLauncher = getSSHLauncher(runTaskResult, template);
		return new RetryingComputerLauncher(sshLauncher);
	}

	private static SSHLauncher getSSHLauncher(RunTaskResult runTaskResult,
			EcsTaskTemplate template) {
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
		int sshPort = 22;
		int port = -1;
		String host = "";
		
		// Wait until container's status becomes RUNNING
		Container ctn = CommonUtils.waitForContainer(taskArn);
		if (!ctn.getLastStatus().equals("RUNNING")) {
			throw new RuntimeException("Container takes too long time to start");
		}

		List<NetworkBinding> nbs = ctn.getNetworkBindings();
		logger.info("Network binding size = " + nbs.size());
		for (NetworkBinding nb : nbs) {
			logger.warning("host = " + nb.getBindIP() + ", port = "
					+ nb.getHostPort());
			if (nb.getContainerPort() == sshPort) {
				port = nb.getHostPort();
				host = nb.getBindIP();
				
				break;
			}
		}

		if (host == "" || port == -1) {
			// Attempt to remove all started tasks if cannot connect to
			// container

			// TODO put to CommonUtils
//			for (Task t : tasks) {
//				StopTaskRequest stopTaskRequest = new StopTaskRequest();
//				stopTaskRequest.setTask(t.getTaskArn());
//				client.stopTask(stopTaskRequest);
//
//			}
			logger.info("Stoped all tasks");
			throw new RuntimeException("Cannot determine host/port to SSH into");
		}

		// host = "54.187.124.238";
//		host = "172.31.4.94";
		logger.info("container's private IP = " + CommonUtils.getTaskContainerPrivateAddress(taskArn));
		logger.info("container's public IP = " + CommonUtils.getTaskContainerPublicAddress(taskArn));
		if (host.equals("0.0.0.0")) {
			host = CommonUtils.getTaskContainerPublicAddress(taskArn);
		}

		logger.info("Creating slave SSH launcher for " + host + ":" + port);

		CommonUtils.waitForPort(host, port);

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
