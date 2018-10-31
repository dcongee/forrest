package com.forrest.data.queue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.forrest.data.dest.ForrestDataDestination;
import com.forrest.data.dest.impl.ForrestDataDestStdout;

public class QueueConsumerThread extends Thread implements Runnable {
	private static Logger logger = Logger.getLogger(QueueConsumerThread.class);

	private BlockingQueue<List<Map<String, Object>>> queue;
	private ForrestDataDestination forrestDataDestination;
	private boolean alived;

	public QueueConsumerThread(BlockingQueue<List<Map<String, Object>>> queue,
			ForrestDataDestination forrestDataDestination, boolean alived) {
		this.queue = queue;
		this.forrestDataDestination = forrestDataDestination;
		this.alived = alived;
	}

	@Override
	public void run() {
		logger.info("start consumer.");
		while (alived) {
			try {
				List<Map<String, Object>> resultList = queue.poll(100, TimeUnit.MILLISECONDS);
				if (resultList != null) {
					forrestDataDestination.deliverDest(resultList);
				}
				// System.out.println(this.currentThread().getState());
			} catch (InterruptedException e) {
				logger.error("consumer error: " + e.getMessage());
			}
		}
	}

	public boolean isAlived() {
		return alived;
	}

	public void setAlived(boolean alived) {
		this.alived = alived;
	}

	public static void main(String[] args) {
		QueueConsumerThread thread = new QueueConsumerThread(new LinkedBlockingQueue<>(), new ForrestDataDestStdout(),
				true);
		thread.start();
		try {
			thread.sleep(10 * 1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		thread.setAlived(false);
	}

}
