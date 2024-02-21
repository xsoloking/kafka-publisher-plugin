# Kafka Publisher Plugin


## Introduction

用于在Pipeline或者Freestyle中发送消息到Kafka的Topic，同时支持Jenkins Queue、Job 开始执行、Job执行Failure状态上报通用消息给Kafka的Topic

## Getting started

* Jenkins System 配置如下下图
  * Name：可以添加多个kafka服务和topic，name用来在pipeline中作为id引用相应的kafka配置，建议只配置一个，所有的类型执行结果都发到一个消息队列，也支持多个
  * brokers还是server url，多个broker以逗号分隔
  * Topic用于设定默认的topicName，同时作为全局监听构建状态的topicName
  * 勾选眶
    * 1: 全局监听Build进入queue，如果Job以`JJB_`作为前缀命名，就会发消息到配置的topic，用来标示消息已经成功消费，进入资源分配环境
    * 2: 全局监听Build获取到资源开始执行，如果Job以`JJB_`作为前缀命名，就会发消息到配置的topic，用于表示任务开始正式执行
    * 3: 全局监听Build构建状态，如果状态是FAILURE并且Job以`JJB_`作为前缀命名，就会发消息到配置的topic，用于异常补偿；通常Build状态通过Pipeline的post build进行状态上报，但是如遇到slave异常断开的场景，postBuild无法执行，异常补偿用来更新服务端的构建状态

![image-20240221230531976](/Users/soloking/Library/Application Support/typora-user-images/image-20240221230531976.png)

* Jenkins Pipeline中发消息的格式如下，可以在sample step中配置生成脚本

  * data 用于手机JSON Message
  * kafkaName用于指定配置某个kafka服务
  * key可以使用taskInstanceId作为key，方便服务端进行数据处理
  * topicName用来接受消息的topic，可以为空，使用kafkaName中选择的kafka配置的默认topic

  ```groovy
  kafkaPublisher data: '{"aaa":"bbb"}', kafkaName: 'kafka', key: 'jenkins-pipeline', topicName: ''
  ```

  ![image-20240221232003885](/Users/soloking/Library/Application Support/typora-user-images/image-20240221232003885.png)

* Jenkins Job进入等待队列的消息示例，其中Job参数中以`id`结尾的参数会添加到消息中，如`taskInstanceId`

```json
{
	"jenkinsJobName": "JJB_ABC",
	"jenkinsBuildStatus": "IN_QUEUE",
	"jenkinsBuildQueueUrl": "http://localhost:8080/jenkins/queue/item/27/api/json?pretty=true",    
	"jenkinsBuildQueueId": 27,
	"taskInstanceId": "123213"
}
```

* Jenkins Job开始执行的消息示例，其中Job参数中以`id`结尾的参数会添加到消息中，如`taskInstanceId`

```json
{
	"jenkinsJobName": "JJB_ABC",
	"jenkinsBuildUrl": "http://localhost:8080/jenkins/job/JJB_ABC/2/",
	"jenkinsBuildStatus": "STARTED",
	"taskInstanceId": "123213",
	"jenkinsBuildId": "2"
}
```

* Jenkins Job执行失败的消息示例，其中Job参数中以`id`结尾的参数会添加到消息中，如`taskInstanceId`

```json
{
	"jenkinsJobName": "JJB_ABC",
	"jenkinsBuildUrl": "http://localhost:8080/jenkins/job/JJB_ABC/2/",
	"jenkinsBuildStatus": "FAILURE",
	"taskInstanceId": "123213",
	"jenkinsBuildId": "2"
}
```





> Jenkins Jobs的几种状态
>
> * IN_QUEUE：Jenkins Build进入等待队列，等待执行（全局监听）
> * STARED：有可用executor比如vm的executor或者动态拉起容器开始执行，（全局监听）
> * SUCCESS：执行成功，（可以全局监听，但是对于有额外信息需要上报的场景需要从pipeline中触发
> * FAILURE：执行失败（全局监听，用于异常补偿）
> * ABORTED：取消（无需监听，主动取消）

## Issues

TODO Decide where you're going to host your issues, the default is Jenkins JIRA, but you can also enable GitHub issues,
If you use GitHub issues there's no need for this section; else add the following line:

Report issues and enhancements in the [Jenkins issue tracker](https://issues.jenkins.io/).

## Contributing

TODO review the default [CONTRIBUTING](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md) file and make sure it is appropriate for your plugin, if not then add your own one adapted from the base file

Refer to our [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)

