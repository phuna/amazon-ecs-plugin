package com.phuna.amazonecs;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.google.common.base.Strings;

public class CommonUtils {

    private static final Logger logger = Logger.getLogger(CommonUtils.class.getName());
    
    private static final int RETRIES = 10;
    private static final int WAIT_TIME_MS = 500;
    
	public static AmazonECSClient getEcsClient(String accessKeyId, String secretAccessKey) {
		AmazonECSClient client = null;
		if (!Strings.isNullOrEmpty(accessKeyId) &&
				!Strings.isNullOrEmpty(secretAccessKey)) {
			BasicAWSCredentials awsCredentials = new BasicAWSCredentials(accessKeyId, secretAccessKey);
			client = new AmazonECSClient(awsCredentials); 
		} else {
			client = new AmazonECSClient();
		}
		
        String endpoint = System.getenv("AWS_ECS_ENDPOINT");
        if (endpoint == null || "".equals(endpoint)) {
            endpoint = "https://ecs.us-west-2.amazonaws.com";
        }
        client.setEndpoint(endpoint);
        String cluster = System.getenv("AWS_ECS_CLUSTER");
        if (cluster == null || "".equals(cluster)) {
            cluster = "default";
        }
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
            if(isPortAvailable(host, port))
                return true;
                        
            try {
                Thread.sleep(WAIT_TIME_MS);
            } catch (InterruptedException e) {
                // no-op
            }
        }
        return false;
    }

    public static Map<String, List<Integer>> parsePorts(String waitPorts) throws IllegalArgumentException,
            NumberFormatException {
        Map<String, List<Integer>> containers = new HashMap<String, List<Integer>>();
        String[] containerPorts = waitPorts.split(System.getProperty("line.separator"));
        for (String container : containerPorts) {
            String[] idPorts = container.split(" ", 2);
            if (idPorts.length < 2)
                throw new IllegalArgumentException("Cannot parse " + idPorts + " as '[conainerId] [port1],[port2],...'");
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
    
    public static Container waitForContainer(AmazonECSClient client, String taskArn) {
    	Container ctn = null;
    	int i = 0;
    	do {
    		DescribeTasksRequest dtr = new DescribeTasksRequest();
            List<String> taskArnList = new ArrayList<String>();
            taskArnList.add(taskArn);
            dtr.setTasks(taskArnList);
            DescribeTasksResult result = client.describeTasks(dtr);
            ctn = result.getTasks().get(0).getContainers().get(0);
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
}
