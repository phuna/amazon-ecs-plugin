package com.phuna.amazonecs;

import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.NetworkBinding;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.StartTaskResult;
import com.amazonaws.services.ecs.model.Task;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.google.common.base.Preconditions;

public class EcsDockerComputerLauncher extends DelegatingComputerLauncher {
    private static final Logger logger = Logger.getLogger(EcsDockerComputerLauncher.class.getName());

	private EcsTaskTemplate template;

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
            for (Failure failure : runTaskResult.getFailures()) {
				logger.warning("Failed task: " + failure);
			}
            List<Task> tasks = runTaskResult.getTasks();
            logger.warning("Tasks = " + tasks.size());
            if (tasks.size() == 0) {
				throw new RuntimeException("No task created");
            }
            
			List<Container> containers = tasks.get(0).getContainers();
			logger.warning("Containers = " + containers.size());
			if (containers.size() == 0) {
				throw new RuntimeException("No container found");
			}
			
			// Find host port for SSH connection
			int sshPort = 22;
			int port = -1;
			String host = "";
			Container ctn = containers.get(0);
			List<NetworkBinding> nbs = ctn.getNetworkBindings();
			for (NetworkBinding nb : nbs) {
				if (nb.getContainerPort() == sshPort) {
					port = nb.getHostPort();
					host = nb.getBindIP();
					break;
				}
			}
			

            logger.log(Level.INFO, "Creating slave SSH launcher for " + host + ":" + port);
            
            PortUtils.waitForPort(host, port);

            StandardUsernameCredentials credentials = SSHLauncher.lookupSystemCredentials(template.credentialsId);

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
