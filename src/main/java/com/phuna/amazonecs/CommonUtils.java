package com.phuna.amazonecs;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class CommonUtils {
	private static final Logger logger = Logger.getLogger(CommonUtils.class
			.getName());

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

        public static boolean waitForPort(String host, int port, int containerStartTimeout) {
     	        while (containerStartTimeout > 0) {
			if (isPortAvailable(host, port))
				return true;

			try {
				Thread.sleep(Constants.WAIT_TIME_S);
			} catch (InterruptedException e) {
				// no-op
			}
			containerStartTimeout -= Constants.WAIT_TIME_S;
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

}
