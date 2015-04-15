package com.phuna.amazonecs;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

public class CommonUtils {

	private static final Logger logger = Logger.getLogger(CommonUtils.class
			.getName());

	private static final int RETRIES = 10;
	private static final int WAIT_TIME_MS = 500;
	private static final String AWS_ECS_ENDPOINT = "https://ecs.us-west-2.amazonaws.com";
	private static final String AWS_EC2_ENDPOINT = "https://ec2.us-west-2.amazonaws.com";

	public static AmazonECSClient getEcsClient() {
		AmazonECSClient client = null;
		if (EcsCloud.getAwsCredentials() != null) {
			client = new AmazonECSClient(EcsCloud.getAwsCredentials());
		} else {
			client = new AmazonECSClient();
		}

		String endpoint = System.getenv("AWS_ECS_ENDPOINT");
		if (endpoint == null || "".equals(endpoint)) {
			endpoint = AWS_ECS_ENDPOINT;
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
			endpoint = AWS_EC2_ENDPOINT;
		}
		client.setEndpoint(endpoint);
		return client;
	}

	public static boolean isPortAvailable(String host, int port) {
		Socket socket = null;
		boolean available = false;
		try {
			socket = new Socket(host, port);
			available = true;
		} catch (IOException e) {
			// no-op
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					// no-op
				}
			}
		}
		return available;
	}

	public static boolean waitForPort(String host, int port) {
		for (int i = 0; i < RETRIES; i++) {
			if (isPortAvailable(host, port))
				return true;

			try {
				Thread.sleep(WAIT_TIME_MS);
			} catch (InterruptedException e) {
				// no-op
			}
		}
		return false;
	}

	public static Map<String, List<Integer>> parsePorts(String waitPorts)
			throws IllegalArgumentException, NumberFormatException {
		Map<String, List<Integer>> containers = new HashMap<String, List<Integer>>();
		String[] containerPorts = waitPorts.split(System
				.getProperty("line.separator"));
		for (String container : containerPorts) {
			String[] idPorts = container.split(" ", 2);
			if (idPorts.length < 2)
				throw new IllegalArgumentException("Cannot parse " + idPorts
						+ " as '[conainerId] [port1],[port2],...'");
			String containerId = idPorts[0].trim();
			String portsStr = idPorts[1].trim();

			List<Integer> ports = new ArrayList<Integer>();
			for (String port : portsStr.split(",")) {
				ports.add(new Integer(port));
			}
			containers.put(containerId, ports);
		}
		return containers;
	}

	public static Container waitForContainer(String taskArn) {
		Container ctn = null;
		int i = 0;
		do {
			DescribeTasksResult dtrr = CommonUtils.describeTasks(taskArn);
			if (dtrr.getTasks().size() == 0 || dtrr.getTasks().get(0).getContainers().size() == 0) {
				throw new RuntimeException("No container found for task ARN: " + taskArn);
			}
			ctn = dtrr.getTasks().get(0).getContainers().get(0);
			if (ctn.getLastStatus().equalsIgnoreCase("RUNNING")) {
				return ctn;
			}
			logger.info("Wait for container's RUNNING");
			try {
				Thread.sleep(WAIT_TIME_MS);
			} catch (InterruptedException e) {
				// No-op
			}
			i++;
		} while (i < RETRIES);
		return ctn;
	}

	public static DescribeTasksResult describeTasks(String... tasksArn) {
		AmazonECSClient ecsClient = CommonUtils.getEcsClient();
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
		AmazonECSClient ecsClient = CommonUtils.getEcsClient();
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
		AmazonEC2Client ec2Client = CommonUtils.getEc2Client();
		DescribeInstancesRequest dir = new DescribeInstancesRequest();
		List<String> ec2InstanceIdList = new ArrayList<String>();
		for (int i = 0; i < ec2InstanceIds.length; i++) {
			ec2InstanceIdList.add(ec2InstanceIds[i]);
		}
		dir.setInstanceIds(ec2InstanceIdList);
		return ec2Client.describeInstances(dir);
	}

	public static DescribeInstancesResult describeInstancesOfTask(String taskArn) {
		DescribeTasksResult dtrr = CommonUtils.describeTasks(taskArn);
		if (dtrr.getTasks().size() == 0) {
			throw new RuntimeException("No task found for task ARN: " + taskArn);
		}
		String containerInstanceArn = dtrr.getTasks().get(0)
				.getContainerInstanceArn();

		DescribeContainerInstancesResult dcirr = CommonUtils
				.describeContainerInstances(containerInstanceArn);
		if (dcirr.getContainerInstances().size() == 0) {
			throw new RuntimeException(
					"No container instances found for task ARN: " + taskArn);
		}
		String ec2InstanceId = dcirr.getContainerInstances().get(0)
				.getEc2InstanceId();

		return CommonUtils.describeInstances(ec2InstanceId);
	}

	public static String getTaskContainerPrivateAddress(String taskArn) {
		DescribeInstancesResult dirr = CommonUtils
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
		DescribeInstancesResult dirr = CommonUtils
				.describeInstancesOfTask(taskArn);
		if (dirr.getReservations().size() == 0
				|| dirr.getReservations().get(0).getInstances().size() == 0) {
			throw new RuntimeException("No EC2 instance found for task ARN: "
					+ taskArn);
		}
		return dirr.getReservations().get(0).getInstances().get(0)
				.getPublicIpAddress();
	}

}
