amazon-ecs-plugin
=================

Jenkins Cloud Plugin for Amazon ECS (EC2 Container Service)

Use Amazon ECS plugin to dynamically provision a slave on Amazon ECS, run a single build on it, then tear down that slave.

Setup Instructions
------------------

- On your configure page, scroll down and click 'Add a New Cloud', and then click 'Amazon ECS'
- Give the cloud a name, and input your Amazon Key Id and Secret Access Key, and the region in which your cluster is running.
  - Right now, each cloud only supports clusters in one region, but could easily be extended to support clusters in multiple regions.
- Add Task Definitions, which are detailed at http://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_defintions.html
- Labels are how you tell your jobs where to run. Under each job that should be run on ECS, specify the label so that it will be run on a container built using this task definition
