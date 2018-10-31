package com.forrest.data.dest.impl;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

public class MQConsumerClient {

	public static void main(String[] args) throws IOException, TimeoutException {

		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("192.168.137.101");
		factory.setPort(5672);
		factory.setUsername("test");
		factory.setPassword("test");
		// 打开连接和创建频道，与发送端一样
		Connection connection = factory.newConnection();
		final Channel channel = connection.createChannel();

		// channel.exchangeDeclare("aa", "direct", true);
		// 声明一个随机队列
		// String queueName = channel.queueDeclare().getQueue();

		// 所有日志严重性级别
		// String[] severities = { "error", "info", "warning" };
		// for (String severity : severities) {
		// // 关注所有级别的日志（多重绑定）
		// channel.queueBind(queueName, "exchange_test", severity);
		// }

		// channel.queueBind("MYSQL_QUEUE", "MYSQL_EXCHANGE", "MYSQL_ROUTING_KEY");

		System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

		// 创建队列消费者
		final Consumer consumer = new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
					byte[] body) throws IOException {
				String message = new String(body, "UTF-8");
				System.out.println(" [x] Received '" + envelope.getRoutingKey() + "':'" + message + "'");
			}
		};
		channel.basicConsume("MYSQL_QUEUE", true, consumer);
	}

}
