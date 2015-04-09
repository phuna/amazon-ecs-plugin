package com.phuna.amazonecs;

import hudson.model.Descriptor.FormException;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProperty;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.util.StreamTaskListener;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.RunTaskResult;

public class EcsTaskTemplate {
	private static final Logger logger = Logger.getLogger(EcsTaskTemplate.class
			.getName());

	private String taskDefinitionArn;
	private String labelString;
	private EcsCloud parent;

	/**
	 * The id of the credentials to use.
	 */
	public String credentialsId;

	@DataBoundConstructor
	public EcsTaskTemplate(String taskDefinitionArn, String labelString,
			String credentialsId) {
		this.taskDefinitionArn = taskDefinitionArn;
		this.labelString = labelString;
		this.credentialsId = credentialsId;
	}

	public String getTaskDefinitionArn() {
		return taskDefinitionArn;
	}

	public void setTaskDefinitionArn(String taskDefinitionArn) {
		this.taskDefinitionArn = taskDefinitionArn;
	}

	public String getLabelString() {
		return labelString;
	}

	public void setLabelString(String labelString) {
		this.labelString = labelString;
	}

	public Set<LabelAtom> getLabelSet() {
		return Label.parse(labelString);
	}

	public String getCredentialsId() {
		return credentialsId;
	}

	public void setCredentialsId(String credentialsId) {
		this.credentialsId = credentialsId;
	}

	public EcsCloud getParent() {
		return parent;
	}

	public void setParent(EcsCloud parent) {
		this.parent = parent;
	}

	public int getSSHLaunchTimeoutMinutes() {
		// TODO Investigate about this later
		return 1;
	}

	public int getNumExecutors() {
		return 1;
	}

	public EcsDockerSlave provision(StreamTaskListener listener)
			throws IOException, FormException {
		PrintStream log = listener.getLogger();

		log.println("Launching " + this.taskDefinitionArn);

		int numExecutors = 1;
		Node.Mode mode = Node.Mode.NORMAL;

		RetentionStrategy retentionStrategy = new OnceRetentionStrategy(
				getSSHLaunchTimeoutMinutes());

		List<? extends NodeProperty<?>> nodeProperties = new ArrayList();

		// InspectContainerResponse containerInspectResponse = provisionNew();
		// String containerId = containerInspectResponse.getId();
		// Start a ECS task, then get task information to pass to
		// DockerComputerLauncher
		RunTaskResult result = provisionNew();

		ComputerLauncher launcher = new EcsDockerComputerLauncher(this, result);

		// Build a description up:
		// String nodeDescription = "Docker Node [" + image + " on ";
		// try {
		// nodeDescription += getParent().getDisplayName();
		// } catch(Exception ex)
		// {
		// nodeDescription += "???";
		// }
		// nodeDescription += "]";
		//
		// String slaveName = containerId.substring(0,12);
		//
		// try
		// {
		// slaveName = slaveName + "@" + getParent().getDisplayName();
		// }
		// catch(Exception ex) {
		// logger.warning("Error fetching name of cloud");
		// }

		return new EcsDockerSlave(this, "slaveName", // slaveName,
				"nodeDescription", // nodeDescription,
				"", // remoteFs,
				numExecutors, // numExecutors,
				mode, labelString, launcher, retentionStrategy, nodeProperties);
	}

	public RunTaskResult provisionNew() {
		AmazonECSClient client = Utils.getEcsClient(parent.getAccessKeyId(),
				parent.getSecretAccessKey());

		// Use Amazon ECS' default scheduler
		RunTaskRequest request = new RunTaskRequest();
		request.setTaskDefinition(taskDefinitionArn);
		List<String> containerInstances = new ArrayList<String>();

		logger.warning("ECS client to perform task = " + client);
		return client.runTask(request);
	}

}
