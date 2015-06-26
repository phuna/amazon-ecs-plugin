package com.phuna.amazonecs;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListContainerInstancesResult;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;

public class EcsCloud extends Cloud implements AwsCloud {

	private static final Logger logger = Logger.getLogger(EcsCloud.class
			.getName());

	private String accessKeyId;
	private String secretAccessKey;
	private String ec2EndPoint;
	private String ecsEndPoint;

	private List<EcsTaskTemplate> templates;
	private boolean sameVPC;
	private AWSCredentials awsCredentials;

	
	
	
	public String getEcsEndPoint() {
		return ecsEndPoint;
	}

	public void setEcsEndPoint(String ecsEndPoint) {
		this.ecsEndPoint = ecsEndPoint;
	}

	public String getEc2EndPoint() {
		return ec2EndPoint;
	}

	public void setEc2EndPoint(String ec2EndPoint) {
		this.ec2EndPoint = ec2EndPoint;
	}

	public String getAccessKeyId() {
		return accessKeyId;
	}

	public void setAccessKeyId(String accessKeyId) {
		this.accessKeyId = accessKeyId;
	}

	public String getSecretAccessKey() {
		return secretAccessKey;
	}

	public void setSecretAccessKey(String secretAccessKey) {
		this.secretAccessKey = secretAccessKey;
	}

	public List<EcsTaskTemplate> getTemplates() {
		return templates;
	}

	public void setTemplates(List<EcsTaskTemplate> templates) {
		this.templates = templates;
	}

	public boolean isSameVPC() {
		return sameVPC;
	}

	public void setSameVPC(boolean sameVPC) {
		this.sameVPC = sameVPC;
	}

	public AWSCredentials getAwsCredentials() {
		if (Strings.isNullOrEmpty(accessKeyId)
				|| Strings.isNullOrEmpty(secretAccessKey)) {
			// return null so that we can use the default credential chain.
			awsCredentials = null;
		} else {
			if (awsCredentials == null) {
				awsCredentials = new BasicAWSCredentials(accessKeyId,
						secretAccessKey);
			}
		}
		return awsCredentials;
	}

	@DataBoundConstructor
	public EcsCloud(String accessKeyId, String secretAccessKey,
			String ec2EndPoint, String ecsEndPoint,
			List<EcsTaskTemplate> templates, String name, boolean sameVPC) {
		super(name);
		logger.warning("*** EcsCloud databound constructor");
		this.accessKeyId = accessKeyId;
		this.secretAccessKey = secretAccessKey;
		this.sameVPC = sameVPC;
		this.ec2EndPoint = ec2EndPoint;
		this.ecsEndPoint = ecsEndPoint;
		
		if (Strings.isNullOrEmpty(accessKeyId)
				|| Strings.isNullOrEmpty(secretAccessKey)) {
			// explicitly set the awsCredentials to null
			this.awsCredentials = null;
		} else {
			awsCredentials = new BasicAWSCredentials(accessKeyId,
					secretAccessKey);
		}
		// logger.warning("*** Create Ecs Client");
		// this.ecsClient = Utils.getEcsClient(this.accessKeyId,
		// this.secretAccessKey);

		if (templates != null) {
			this.templates = templates;
		} else {
			this.templates = new ArrayList<EcsTaskTemplate>();
		}
		for (EcsTaskTemplate template : templates) {
			template.setParent(this);
		}

	}

	@Override
	public Collection<PlannedNode> provision(Label label, int excessWorkload) {
		try {
			logger.warning("*** provision, EcsCloud = " + this);
			logger.log(Level.INFO, "Asked to provision {0} slave(s) for: {1}",
					new Object[] { excessWorkload, label });

			List<NodeProvisioner.PlannedNode> r = new ArrayList<NodeProvisioner.PlannedNode>();

			final EcsTaskTemplate t = getTemplate(label);

			logger.log(Level.INFO, "Will provision \"{0}\" for: {1}",
					new Object[] { t.getTaskDefinitionArn(), label });

			while (excessWorkload > 0) {
				r.add(new NodeProvisioner.PlannedNode(t.getTaskDefinitionArn(),
						Computer.threadPoolForRemoting
								.submit(new Callable<Node>() {
									public Node call() throws Exception {
										EcsDockerSlave slave = null;
										try {
											slave = t
													.provision(new StreamTaskListener(
															System.out));
											final Jenkins jenkins = Jenkins
													.getInstance();
											// TODO once the baseline is 1.592+
											// switch to Queue.withLock
											synchronized (jenkins.getQueue()) {
												jenkins.addNode(slave);
											}
											// Docker instances may have a long
											// init script. If we declare
											// the provisioning complete by
											// returning without the connect
											// operation, NodeProvisioner may
											// decide that it still wants
											// one more instance, because it
											// sees that (1) all the slaves
											// are offline (because it's still
											// being launched) and
											// (2) there's no capacity
											// provisioned yet.
											//
											// deferring the completion of
											// provisioning until the launch
											// goes successful prevents this
											// problem.
											slave.toComputer().connect(false)
													.get();
											return slave;
										} catch (Exception ex) {
											logger.log(Level.SEVERE,
													"Error in provisioning; slave="
															+ slave
															+ ", template=" + t);

											ex.printStackTrace();
											throw Throwables.propagate(ex);
										} finally {
											// TODO Decrease container counter??
										}
									}
								}), t.getNumExecutors()));

				excessWorkload -= t.getNumExecutors();

			}
			return r;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Exception while provisioning for: "
					+ label, e);
			return Collections.emptyList();
		}
	}

	@Override
	public boolean canProvision(Label label) {

		// Set<LabelAtom> labelSet = Label.parse(labelString);
		// return label == null || label.matches(labelSet);
		return getTemplate(label) != null;
	}

	public EcsTaskTemplate getTemplate(Label label) {
		if (label == null && templates.size() > 0) {
			return templates.get(0);
		}
		for (EcsTaskTemplate t : templates) {
			if (label.matches(t.getLabelSet())) {
				return t;
			}
		}
		return null;
	}

	public AmazonECSClient getEcsClient() {
		AmazonECSClient client = getAwsCredentials() == null ? new AmazonECSClient()
				: new AmazonECSClient(getAwsCredentials());

		
		String endpoint = Strings.isNullOrEmpty(this.getEcsEndPoint()) ? System
				.getenv("AWS_ECS_ENDPOINT") : this.getEcsEndPoint();

		if (Strings.isNullOrEmpty(endpoint)) {
			endpoint = Constants.AWS_ECS_ENDPOINT;
		}
		client.setEndpoint(endpoint);
		logger.log(Level.SEVERE, "AWS ECS Endpoint: " + endpoint);

		return client;
	}

	public static AmazonECSClient getEcsClient(String accessKeyId,
			String secretAccessKey, String ecsEndPoint) {
		AmazonECSClient client;
		if (Strings.isNullOrEmpty(accessKeyId)
				|| Strings.isNullOrEmpty(secretAccessKey)) {
			logger.log(Level.SEVERE, "No Access Key provided");
			client = new AmazonECSClient();
		} else {
			client = new AmazonECSClient(new BasicAWSCredentials(accessKeyId,
					secretAccessKey));
		}
		String endpoint = Strings.isNullOrEmpty(ecsEndPoint) ? System
				.getenv("AWS_ECS_ENDPOINT") : ecsEndPoint;

		if (Strings.isNullOrEmpty(endpoint)) {
			endpoint = Constants.AWS_ECS_ENDPOINT;
		}
		client.setEndpoint(endpoint);
		logger.log(Level.SEVERE, "AWS ECS Endpoint: " + endpoint);

		return client;
	}

	public AmazonEC2Client getEc2Client() {
		AmazonEC2Client client = getAwsCredentials() == null ? new AmazonEC2Client()
				: new AmazonEC2Client(getAwsCredentials());
		
		String endpoint = Strings.isNullOrEmpty(this.getEc2EndPoint()) ? System
				.getenv("AWS_EC2_ENDPOINT") : this.getEc2EndPoint();

		if (Strings.isNullOrEmpty(endpoint)) {
			endpoint = Constants.AWS_EC2_ENDPOINT;
		}
		client.setEndpoint(endpoint);
		logger.log(Level.SEVERE, "AWS EC2 Endpoint: " + endpoint);

		return client;
	}

	public static AmazonEC2Client getEc2Client(String accessKeyId,
			String secretAccessKey, String ec2EndPoint) {
		AmazonEC2Client client;

		if (Strings.isNullOrEmpty(accessKeyId)
				|| Strings.isNullOrEmpty(secretAccessKey)) {
			logger.log(Level.SEVERE, "No Access Key provided");
			client = new AmazonEC2Client();
		} else {
			client = new AmazonEC2Client(new BasicAWSCredentials(accessKeyId,
					secretAccessKey));
		}

		String endpoint = Strings.isNullOrEmpty(ec2EndPoint) ? System
				.getenv("AWS_EC2_ENDPOINT") : ec2EndPoint;

		if (Strings.isNullOrEmpty(endpoint)) {
			endpoint = Constants.AWS_EC2_ENDPOINT;
		}
		client.setEndpoint(endpoint);
		logger.log(Level.SEVERE, "AWS EC2 Endpoint: " + endpoint);

		return client;
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<Cloud> {
		@Override
		public String getDisplayName() {
			return "Amazon ECS";
		}

		public FormValidation doTestConnection(
				@QueryParameter String accessKeyId,
				@QueryParameter String secretAccessKey,
				@QueryParameter String ecsEndPoint) {

			if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
				logger.warning("No Credentials provided, using  DefaultAWSCredentialsProviderChain ");

			}
			logger.warning("ec2EndPoint=" + ecsEndPoint);
			ecsEndPoint = ecsEndPoint == null ? "" : ecsEndPoint;
			AmazonECSClient client = EcsCloud.getEcsClient(accessKeyId,
					secretAccessKey, ecsEndPoint);
			ListClustersResult clusters = client.listClusters();
			logger.log(Level.SEVERE, "Clusters: "
					+ clusters.getClusterArns().size() + " clusters:"
					+ clusters);
			ListContainerInstancesResult result = client
					.listContainerInstances();

			return FormValidation.ok("Success. Number of container instances: "
					+ result.getContainerInstanceArns().size());
		}
		//
		// public ListBoxModel doFillCredentialsIdItems(@AncestorInPath
		// ItemGroup context) {
		//
		// List<StandardCertificateCredentials> credentials =
		// CredentialsProvider.lookupCredentials(StandardCertificateCredentials.class,
		// context);
		//
		// return new CredentialsListBoxModel().withEmptySelection()
		// .withMatching(CredentialsMatchers.always(),
		// credentials);
		// }
	}
}
