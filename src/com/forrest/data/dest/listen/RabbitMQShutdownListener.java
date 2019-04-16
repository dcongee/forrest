package com.forrest.data.dest.listen;

import org.apache.log4j.Logger;

import com.rabbitmq.client.Method;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;

public class RabbitMQShutdownListener implements ShutdownListener {
	private static Logger logger = Logger.getLogger(RabbitMQShutdownListener.class);

	public RabbitMQShutdownListener() {
	}

	@Override
	public void shutdownCompleted(ShutdownSignalException cause) {
		Method m = cause.getReason();
		String hardError = "";
		String applInit = "";

		if (cause.isHardError()) {
			hardError = "connection";
		} else {
			hardError = "channel";
		}

		if (cause.isInitiatedByApplication()) {
			applInit = "application";
		} else {
			applInit = "broker";
		}
		// logger.error("catche " + hardError + " ShutdownSignalException. it was caused
		// by " + applInit + " at the "
		// + hardError + " level.the close reason protocolClassID is: " +
		// m.protocolClassId()
		// + "; protocolMethodId is: " + m.protocolMethodId() + "; protocolMethodName
		// is:" + m.protocolMethodName()
		// + "; ShutdownSignalException message is:" + cause.getMessage());

		logger.error("catche " + hardError + " ShutdownSignalException. it was caused by " + applInit + " at the "
				+ hardError + " level.the close reason is: " + m + "; ShutdownSignalException message is:"
				+ cause.getMessage());
		// if (!cause.isHardError()) {
		// logger.info("begin to restart consumer...");
		// this.rabbitClient.reStartRabbitConsumer();
		// }

	}
}
