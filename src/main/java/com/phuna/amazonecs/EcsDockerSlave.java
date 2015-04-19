package com.phuna.amazonecs;

import hudson.model.TaskListener;
import hudson.model.Descriptor.FormException;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class EcsDockerSlave extends AbstractCloudSlave {
	private static final Logger logger = Logger.getLogger(AWSUtils.class
			.getName());

	private EcsTaskTemplate template;
	private String taskArn;

	public EcsDockerSlave(EcsTaskTemplate template, String taskArn,
			String nodeDescription, String remoteFS, int numExecutors,
			Mode mode, String labelString, ComputerLauncher launcher,
			RetentionStrategy retentionStrategy,
			List<? extends NodeProperty<?>> nodeProperties)
			throws FormException, IOException {
		super(taskArn, nodeDescription, remoteFS, numExecutors, mode,
				labelString, launcher, retentionStrategy, nodeProperties);
		this.template = template;
		this.taskArn = taskArn;
	}

	@Override
	public AbstractCloudComputer createComputer() {
		return new EcsDockerComputer(this);
	}

	@Override
	protected void _terminate(TaskListener listener) throws IOException,
			InterruptedException {
		toComputer().disconnect(null);
		logger.info("Stop task " + taskArn);
		AWSUtils.stopTask(template.getParent(), taskArn, template.getParent().isSameVPC());
	}

}
