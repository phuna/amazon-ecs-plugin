amazon-ecs-plugin
=================

Jenkins Cloud Plugin for Amazon ECS (EC2 Container Service)

Use Amazon ECS plugin to dynamically provision a slave on Amazon ECS, run a single build on it, then tear down that slave.

Configuration
------------------
### Global
- On your configuration page, scroll down and click 'Add a New Cloud', and then click 'Amazon ECS'
- Give the cloud a name, and input your Amazon Key Id and Secret Access Key, and the region in which your cluster is running.
  - Right now, each cloud only supports clusters in one region, but could easily be extended to support clusters in multiple regions.

### Task Definitions
- Add Task Definitions on your AWS account, which are detailed at http://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_defintions.html
- Copy the **full** ARN into the task definition ARN field
- Labels are how you tell your jobs where to run. Under each job that should be run on ECS, specify the label so that it will be run on a container built using this task definition
- Specify a workspace directory on the slave if you wish (the default is /home/jenkins)
- add a Container Start Timeout (in seconds), which defines how long the plugin waits for your image to be started before giving up
