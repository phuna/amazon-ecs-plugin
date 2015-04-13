package com.phuna.amazonecs;

import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.RuntimeErrorException;

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
    private static final Logger logger = Logger.getLogger(EcsDockerComputerLauncher.class.getName());

//	private EcsTaskTemplate template;

	protected EcsDockerComputerLauncher(EcsTaskTemplate template, RunTaskResult runTaskResult) {
		super(makeLauncher(template, runTaskResult));
	}

	private static ComputerLauncher makeLauncher(EcsTaskTemplate template, RunTaskResult runTaskResult) {
		SSHLauncher sshLauncher = getSSHLauncher(runTaskResult, template);
        return new RetryingComputerLauncher(sshLauncher);
    }

    private static SSHLauncher getSSHLauncher(RunTaskResult runTaskResult, EcsTaskTemplate template)   {
        Preconditions.checkNotNull(template);

//        try {

//            ExposedPort sshPort = new ExposedPort(22);
//            int port = 22;
//            String host = null;

//            Ports.Binding[] bindings = detail.getNetworkSettings().getPorts().getBindings().get(sshPort);
//
//            for(Ports.Binding b : bindings) {
//                port = b.getHostPort();
//                host = b.getHostIp();
//            }

//            if (host == null) {
//                URL hostUrl = new URL(template.getParent().serverUrl);
//                host = hostUrl.getHost();
//            }
            
            // TODO Find SSH connection information
        	AmazonECSClient client = CommonUtils.getEcsClient(template.getParent().getAccessKeyId(), template.getParent().getSecretAccessKey());
        	
            for (Failure failure : runTaskResult.getFailures()) {
				logger.info("Failed task: " + failure);
			}
            List<Task> tasks = runTaskResult.getTasks();
            logger.info("Tasks = " + tasks.size());
            if (tasks.size() == 0) {
				throw new RuntimeException("No task created");
            }
            
			List<Container> containers = tasks.get(0).getContainers();
			logger.info("Containers = " + containers.size());
			if (containers.size() == 0) {
				throw new RuntimeException("No container found");
			}
			
			// Find host port for SSH connection
			int sshPort = 22;
			int port = -1;
			String host = "";
			Container ctn = containers.get(0);
			logger.info("name = " + ctn.getName() + ", containerArn = " + ctn.getContainerArn() + ", taskArn = " + ctn.getTaskArn());

			// Wait until container's status becomes RUNNING
			ctn = CommonUtils.waitForContainer(client, tasks.get(0).getTaskArn());
			if (!ctn.getLastStatus().equals("RUNNING")) {
				throw new RuntimeException("Container takes too long time to start");
			}
			
			List<NetworkBinding> nbs = ctn.getNetworkBindings();
			logger.info("Network binding size = " + nbs.size());
			for (NetworkBinding nb : nbs) {
				logger.warning("host = " + nb.getBindIP() + ", port = " + nb.getHostPort());
				if (nb.getContainerPort() == sshPort) {
					port = nb.getHostPort();
					host = nb.getBindIP();
					break;
				}
			}
			
			if (host == "" || port == -1) {
				// Attempt to remove all started tasks if cannot connect to container
	        	
//	        	for (Task t : tasks) {
//					StopTaskRequest stopTaskRequest = new StopTaskRequest();
//					stopTaskRequest.setTask(t.getTaskArn());
//					client.stopTask(stopTaskRequest);
//					
//				}
//	        	logger.warning("Stoped all tasks");
				throw new RuntimeException("Cannot determine host/port to SSH into");
			}
			
			// TODO find container instance's IP to use
			host = "54.187.124.238";

            logger.info("Creating slave SSH launcher for " + host + ":" + port);
            
            CommonUtils.waitForPort(host, port);

            StandardUsernameCredentials credentials = SSHLauncher.lookupSystemCredentials(template.getCredentialsId());

//            return new SSHLauncher();
            return new SSHLauncher(host, port, credentials,  
            		"", // jvmOptions 
            		"", // javaPath, 
            		"", // prefixStartSlaveCmd 
            		"", // suffixStartSlaveCmd 
            		template.getSSHLaunchTimeoutMinutes() * 60);

//        } catch (Exception e) {
//            throw new RuntimeException("Error: " + e);
//        }
    }

}
