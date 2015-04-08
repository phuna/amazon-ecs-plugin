package com.phuna.amazonecs;

import hudson.slaves.AbstractCloudComputer;

import java.util.logging.Logger;

public class EcsDockerComputer extends AbstractCloudComputer<EcsDockerSlave> {
    private static final Logger logger = Logger.getLogger(EcsDockerComputer.class.getName());

	public EcsDockerComputer(EcsDockerSlave slave) {
		super(slave);
	}
}
