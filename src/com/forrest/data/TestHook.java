package com.forrest.data;

import java.lang.Thread.State;

public class TestHook extends Thread implements Runnable {

	public boolean hookFlag;

	public boolean isHookFlag() {
		return hookFlag;
	}

	public void setHookFlag(boolean hookFlag) {
		this.hookFlag = hookFlag;
	}

	public void addShutdownHook(TestHook thread) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("begin to stop forrest data...");
				thread.setHookFlag(false);
				Long currentTime = System.currentTimeMillis();
				while (System.currentTimeMillis() - currentTime < 30 * 1000) {
					if (thread.getState() == State.TERMINATED) {
						System.out.println("stop forrest success.");
						return;
					}
					try {
						this.sleep(100);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				System.out.println("force to close forrest data.");
			}
		});

	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		while (hookFlag) {
			System.out.println(this.currentThread().getName() + " is running!");
			try {
				this.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	public static void main(String[] args) {
		TestHook t = new TestHook();
		t.setHookFlag(true);
		t.start();
		t.addShutdownHook(t);
	}

}
