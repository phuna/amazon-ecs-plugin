package com.phuna.amazonecs;

import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.util.logging.Logger;


public class RetryingComputerLauncher extends DelegatingComputerLauncher {

    private static final Logger logger = Logger.getLogger(RetryingComputerLauncher.class.getName());

    /**
     * time (ms) to back off between retries?
     */
    private final int pause   = 5000;

    /**
     * Let us know when to pause the launch.
     */
    private boolean hasTried = false;

    public RetryingComputerLauncher(ComputerLauncher delegate) {
        super(delegate);
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        if (hasTried) {
            logger.info("Launch failed, pausing before retry.");
            Thread.sleep(pause);
        }
        super.launch(computer, listener);
        hasTried = true;
    }
}
