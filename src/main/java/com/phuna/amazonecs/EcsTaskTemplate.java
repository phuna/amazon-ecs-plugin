package com.phuna.amazonecs;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.ItemGroup;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.slaves.NodeProperty;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.util.StreamTaskListener;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.Task;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.google.common.base.Strings;
import com.trilead.ssh2.Connection;

public class EcsTaskTemplate implements Describable<EcsTaskTemplate> {
	private static final Logger logger = Logger.getLogger(EcsTaskTemplate.class
			.getName());

	private String taskDefinitionArn;
	private String labelString;
	private EcsCloud parent;
	private String remoteFS; // Location on slave used as workspace for Jenkins' slave
	// private AmazonECSClient ecsClient;

	/**
	 * The id of the credentials to use.
	 */
	private String credentialsId;

	@DataBoundConstructor
	public EcsTaskTemplate(String taskDefinitionArn, String labelString,
			String remoteFS,
			String credentialsId) {
		this.taskDefinitionArn = taskDefinitionArn;
		this.labelString = labelString;
		this.credentialsId = credentialsId;
		this.remoteFS = Strings.isNullOrEmpty(remoteFS) ? "/home/jenkins" : remoteFS;
		
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

	public String getRemoteFS() {
		return remoteFS;
	}

	public void setRemoteFS(String remoteFS) {
		this.remoteFS = remoteFS;
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

		// Start a ECS task, then get task information to pass to
		// DockerComputerLauncher
		RunTaskResult result = provisionNew();
		if (result.getTasks().size() == 0) {
			// Error occured, no tasks created
			result.getFailures();
			for (Failure f : result.getFailures()) {
				logger.warning(f.getReason());
			}
			throw new RuntimeException("Failed to launch task");
		}
		if (result.getTasks().get(0).getContainers().size() == 0) {
			throw new RuntimeException("Task launched but no container found");
		}

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
		
		Task t = result.getTasks().get(0);
		Container ctn = t.getContainers().get(0);
		logger.info("taskArn = " + ctn.getTaskArn() + ", containerArn = " + ctn.getContainerArn() + ", name = " + ctn.getName());
		return new EcsDockerSlave(this,
				t.getTaskArn(), // slaveName,
				ctn.getContainerArn(), // nodeDescription,
				this.remoteFS, // remoteFs,
				numExecutors, // numExecutors,
				mode, labelString, launcher, retentionStrategy, nodeProperties);
	}

	public RunTaskResult provisionNew() {
		// logger.warning("parent = " + parent);

		// Use Amazon ECS' default scheduler
		RunTaskRequest request = new RunTaskRequest();
		request.setTaskDefinition(taskDefinitionArn);

		AmazonECSClient client = getParent().getEcsClient();
		return client.runTask(request);
	}

	@Override
	public Descriptor<EcsTaskTemplate> getDescriptor() {
		return Jenkins.getInstance().getDescriptor(getClass());
	}

	@Extension
	public static final class DescriptorImpl extends
			Descriptor<EcsTaskTemplate> {

		@Override
		public String getDisplayName() {
			return "Ecs Task Template";
		}

		public ListBoxModel doFillCredentialsIdItems(
				@AncestorInPath ItemGroup context) {

			return new SSHUserListBoxModel().withMatching(SSHAuthenticator
					.matcher(Connection.class), CredentialsProvider
					.lookupCredentials(StandardUsernameCredentials.class,
							context, ACL.SYSTEM, SSHLauncher.SSH_SCHEME));
		}
	}

}
