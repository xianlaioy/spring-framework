/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.sockjs.server;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.sockjs.SockJsHandler;


/**
 *
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public interface SockJsConfiguration {


	/**
	 * Streaming transports save responses on the client side and don't free
	 * memory used by delivered messages. Such transports need to recycle the
	 * connection once in a while. This property sets a minimum number of bytes
	 * that can be send over a single HTTP streaming request before it will be
	 * closed. After that client will open a new request. Setting this value to
	 * one effectively disables streaming and will make streaming transports to
	 * behave like polling transports.
	 * <p>
	 * The default value is 128K (i.e. 128 * 1024).
	 */
	public int getStreamBytesLimit();

	/**
	 * The amount of time in milliseconds before a client is considered
	 * disconnected after not having a receiving connection, i.e. an active
	 * connection over which the server can send data to the client.
	 * <p>
	 * The default value is 5000.
	 */
	public long getDisconnectDelay();

	/**
	 * The amount of time in milliseconds when the server has not sent any
	 * messages and after which the server should send a heartbeat frame to the
	 * client in order to keep the connection from breaking.
	 * <p>
	 * The default value is 25,000 (25 seconds).
	 */
	public long getHeartbeatTime();

	/**
	 * A scheduler instance to use for scheduling heartbeat frames.
	 * <p>
	 * By default a {@link ThreadPoolTaskScheduler} with default settings is used.
	 */
	public TaskScheduler getHeartbeatScheduler();

	/**
	 * Provides access to the {@link SockJsHandler} that will handle the request. This
	 * method should be called once per SockJS session. It may return the same or a
	 * different instance every time it is called.
	 */
	SockJsHandler getSockJsHandler();

}