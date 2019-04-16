package com.forrest.data.dest.listen;

import org.apache.log4j.Logger;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;

public class RabbitMQRecoveryListener implements RecoveryListener {
	private Logger logger = Logger.getLogger(RabbitMQRecoveryListener.class);

	public RabbitMQRecoveryListener() {

	}

	@Override
	public void handleRecovery(Recoverable recoverable) {
		// TODO Auto-generated method stub
		if (recoverable instanceof Connection) {
			logger.info("handle connection recover.");
		} else if (recoverable instanceof Channel) {
			int channelNumber = ((Channel) recoverable).getChannelNumber();
			logger.info("handle recover channel #" + channelNumber + " was recovered.");
		}
	}

	@Override
	public void handleRecoveryStarted(Recoverable recoverable) {
		// TODO Auto-generated method stub
		if (recoverable instanceof Connection) {
			logger.info("Connection was recovered.");
		} else if (recoverable instanceof Channel) {
			int channelNumber = ((Channel) recoverable).getChannelNumber();
			logger.info("Connection to channel #" + channelNumber + " was recovered.");
		}

	}

}
