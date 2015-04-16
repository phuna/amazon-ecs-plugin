package com.phuna.amazonecs;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesResult;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.StopTaskRequest;
import com.amazonaws.services.ecs.model.Task;

public class AWSUtils {
	private static final Logger logger = Logger.getLogger(AWSUtils.class
			.getName());

	public static AmazonECSClient getEcsClient() {
		AmazonECSClient client = null;
		if (EcsCloud.getAwsCredentials() != null) {
			client = new AmazonECSClient(EcsCloud.getAwsCredentials());
		} else {
			client = new AmazonECSClient();
		}

		String endpoint = System.getenv("AWS_ECS_ENDPOINT");
		if (endpoint == null || "".equals(endpoint)) {
			endpoint = Constants.AWS_ECS_ENDPOINT;
		}
		client.setEndpoint(endpoint);
		return client;
	}

	public static AmazonEC2Client getEc2Client() {
		AmazonEC2Client client = null;
		if (EcsCloud.getAwsCredentials() != null) {
			client = new AmazonEC2Client(EcsCloud.getAwsCredentials());
		} else {
			client = new AmazonEC2Client();
		}

		String endpoint = System.getenv("AWS_EC2_ENDPOINT");
		if (endpoint == null || "".equals(endpoint)) {
			endpoint = Constants.AWS_EC2_ENDPOINT;
		}
		client.setEndpoint(endpoint);
		return client;
	}

	public static Container waitForContainer(String taskArn) {
		Container ctn = null;
		int i = 0;
		do {
			DescribeTasksResult dtrr = AWSUtils.describeTasks(taskArn);
			if (dtrr.getTasks().size() == 0
					|| dtrr.getTasks().get(0).getContainers().size() == 0) {
				throw new RuntimeException("No container found for task ARN: "
						+ taskArn);
			}
			ctn = dtrr.getTasks().get(0).getContainers().get(0);
			if (ctn.getLastStatus().equalsIgnoreCase("RUNNING")) {
				return ctn;
			}
			logger.info("Wait for container's RUNNING");
			try {
				Thread.sleep(Constants.WAIT_TIME_MS);
			} catch (InterruptedException e) {
				// No-op
			}
			i++;
		} while (i < Constants.RETRIES);
		return ctn;
	}

	public static DescribeTasksResult describeTasks(String... tasksArn) {
		AmazonECSClient ecsClient = AWSUtils.getEcsClient();
		DescribeTasksRequest dtr = new DescribeTasksRequest();
		List<String> taskArnList = new ArrayList<String>();
		for (int i = 0; i < tasksArn.length; i++) {
			taskArnList.add(tasksArn[i]);
		}
		dtr.setTasks(taskArnList);
		return ecsClient.describeTasks(dtr);
	}

	public static DescribeContainerInstancesResult describeContainerInstances(
			String... containerInstancesArn) {
		AmazonECSClient ecsClient = AWSUtils.getEcsClient();
		DescribeContainerInstancesRequest dcir = new DescribeContainerInstancesRequest();
		List<String> containerInstanceArnList = new ArrayList<String>();
		for (int i = 0; i < containerInstancesArn.length; i++) {
			containerInstanceArnList.add(containerInstancesArn[i]);
		}
		dcir.setContainerInstances(containerInstanceArnList);
		return ecsClient.describeContainerInstances(dcir);
	}

	public static DescribeInstancesResult describeInstances(
			String... ec2InstanceIds) {
		AmazonEC2Client ec2Client = AWSUtils.getEc2Client();
		DescribeInstancesRequest dir = new DescribeInstancesRequest();
		List<String> ec2InstanceIdList = new ArrayList<String>();
		for (int i = 0; i < ec2InstanceIds.length; i++) {
			ec2InstanceIdList.add(ec2InstanceIds[i]);
		}
		dir.setInstanceIds(ec2InstanceIdList);
		return ec2Client.describeInstances(dir);
	}

	public static DescribeInstancesResult describeInstancesOfTask(String taskArn) {
		DescribeTasksResult dtrr = AWSUtils.describeTasks(taskArn);
		if (dtrr.getTasks().size() == 0) {
			throw new RuntimeException("No task found for task ARN: " + taskArn);
		}
		String containerInstanceArn = dtrr.getTasks().get(0)
				.getContainerInstanceArn();

		DescribeContainerInstancesResult dcirr = AWSUtils
				.describeContainerInstances(containerInstanceArn);
		if (dcirr.getContainerInstances().size() == 0) {
			throw new RuntimeException(
					"No container instances found for task ARN: " + taskArn);
		}
		String ec2InstanceId = dcirr.getContainerInstances().get(0)
				.getEc2InstanceId();

		return AWSUtils.describeInstances(ec2InstanceId);
	}

	public static String getTaskContainerPrivateAddress(String taskArn) {
		DescribeInstancesResult dirr = AWSUtils
				.describeInstancesOfTask(taskArn);
		if (dirr.getReservations().size() == 0
				|| dirr.getReservations().get(0).getInstances().size() == 0) {
			throw new RuntimeException("No EC2 instance found for task ARN: "
					+ taskArn);
		}
		return dirr.getReservations().get(0).getInstances().get(0)
				.getPrivateIpAddress();
	}

	public static String getTaskContainerPublicAddress(String taskArn) {
		DescribeInstancesResult dirr = AWSUtils
				.describeInstancesOfTask(taskArn);
		if (dirr.getReservations().size() == 0
				|| dirr.getReservations().get(0).getInstances().size() == 0) {
			throw new RuntimeException("No EC2 instance found for task ARN: "
					+ taskArn);
		}
		return dirr.getReservations().get(0).getInstances().get(0)
				.getPublicIpAddress();
	}

	public static void cleanUpTasks(RunTaskResult rtr) {
		logger.info("*** Cleanup tasks");
		StopTaskRequest str = null;
		AmazonECSClient client = AWSUtils.getEcsClient();
		if (rtr.getTasks().size() != 0) {
			List<Task> tasks = rtr.getTasks();
			for (Task task : tasks) {
				str = new StopTaskRequest();
				str.setTask(task.getTaskArn());
				client.stopTask(str);
			}
		}
	}
	
	public static void stopTask(String taskArn) {
		StopTaskRequest str = new StopTaskRequest();
		AmazonECSClient client = AWSUtils.getEcsClient();
		str.setTask(taskArn);
		client.stopTask(str);
	}
}
