/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.forrest.http;

import org.apache.log4j.Logger;

import com.forrest.monitor.ForrestMonitor;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class ForrestHttpServer extends Thread implements Runnable {
	private Logger logger = Logger.getLogger(ForrestHttpServer.class);
	private int port;
	private ForrestMonitor forrestMonitor;

	public ForrestHttpServer(int port, ForrestMonitor forrestMonitor) {
		this.port = port;
		this.forrestMonitor = forrestMonitor;
		logger.info("http listening port:" + port);
	}

	public void run() {
		// Configure the server.
		EventLoopGroup bossGroup = new NioEventLoopGroup(1);
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.option(ChannelOption.SO_BACKLOG, 1024);
			b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
					.handler(new LoggingHandler(LogLevel.INFO))
					.childHandler(new ForrestHttpServerInitializer(forrestMonitor));

			Channel ch;
			try {
				ch = b.bind(port).sync().channel();
				ch.closeFuture().sync();
			} catch (InterruptedException e) {
				logger.error("forrest http server start failed.");
				e.printStackTrace();
				System.exit(1);
			}

		} finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}

	public static void main(String[] args) throws Exception {
		// new ForrestHttpServer(8080).start();
	}
}
