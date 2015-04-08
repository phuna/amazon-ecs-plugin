package com.phuna.amazonecs;

import java.io.IOException;
import java.util.List;

import hudson.model.TaskListener;
import hudson.model.Descriptor.FormException;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;

public class EcsDockerSlave extends AbstractCloudSlave {
	private EcsTaskTemplate template;

	public EcsDockerSlave(EcsTaskTemplate template, String name, String nodeDescription, String remoteFS,
			int numExecutors, Mode mode, String labelString,
			ComputerLauncher launcher, RetentionStrategy retentionStrategy,
			List<? extends NodeProperty<?>> nodeProperties)
			throws FormException, IOException {
		super(name, nodeDescription, remoteFS, numExecutors, mode, labelString,
				launcher, retentionStrategy, nodeProperties);
		this.template = template;
	}

	@Override
	public AbstractCloudComputer createComputer() {
		// TODO Implement
		return null;
	}

	@Override
	protected void _terminate(TaskListener listener) throws IOException,
			InterruptedException {
		// TODO Auto-generated method stub

	}

}
